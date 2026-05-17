@acceptance @e2e
Feature: 휴대폰 실시간 스캔 업로드를 서버에 저장한다

  Background:
    * url baseUrl

  Scenario: 실시간 스캔을 시작하고 프레임과 최종 파일을 업로드하면 활성 스캔이 된다
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Acceptance Building', description: 'created by Karate acceptance test', latitude: 36.764, longitude: 127.282 }
    When method post
    Then status 201
    * def buildingId = response.buildingId

    Given path 'api', 'v1', 'buildings', buildingId, 'floors'
    And request { name: '2F', level: 2, height: 3.2 }
    When method post
    Then status 201
    * def floorId = response.floorId

    * def streaming = support.twoLinkedFramesStreamingScan()
    Given path 'api', 'v1', 'floors', floorId, 'scans', 'start'
    And request streaming.startBody
    When method post
    Then status 201
    And match response.scanId == streaming.scanId
    And match response.floorId == floorId
    And match response.state == 'STARTED'
    And assert support.rtabmapTableExists(streaming.scanId, 'Node')
    And assert support.rtabmapTableExists(streaming.scanId, 'Data')
    And assert support.rtabmapTableExists(streaming.scanId, 'Link')

    Given path 'api', 'v1', 'scans', streaming.scanId, 'frames'
    And request streaming.framesBody
    When method post
    Then status 200
    And match response.framesApplied == 2
    And match response.linksApplied == 1
    And match response.lastNodeId == 2
    And match response.nodeCount == 2
    And assert support.rtabmapRowCount(streaming.scanId, 'Node') == 2
    And assert support.rtabmapRowCount(streaming.scanId, 'Data') == 2
    And assert support.rtabmapRowCount(streaming.scanId, 'Link') == 1

    Given path 'api', 'v1', 'scans', streaming.scanId, 'finalize'
    And multipart file manifest = { read: '#(streaming.manifestReadPath)', filename: 'manifest.json', contentType: 'application/json' }
    And multipart file metadata = { read: '#(streaming.metadataReadPath)', filename: 'scan_metadata.db', contentType: 'application/octet-stream' }
    When method post
    Then status 200
    And match response.scanId == streaming.scanId
    And match response.state == 'READY'
    And match response.nodeCount == 2
    And match response.keyframeCount == 2
    And match response.payloadSha256 != ''
    And assert support.scanFileExists(streaming.scanId, 'manifest.json')
    And assert support.scanFileExists(streaming.scanId, 'scan_metadata.db')
    And assert support.rtabmapRowCount(streaming.scanId, 'Node') == 2

    Given path 'api', 'v1', 'floors', floorId, 'scans', 'chunks'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].scanId == streaming.scanId
    And match response[0].fileName == streaming.scanId + '.db'
    And match response[0].status == 'READY'
    And match response[0].active == true

    Given path 'api', 'v1', 'floors', floorId, 'build'
    And request {}
    When method post
    Then status 200
    And match response.scanId == streaming.scanId
    And match response.status == 'QUEUED'
    And match response.buildJobId != ''
