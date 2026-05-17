@acceptance @e2e
Feature: 업로드한 실내 스캔으로 길찾기 가능한 층 지도를 만든다

  Background:
    * url baseUrl

  Scenario: 스캔 파일을 업로드하면 층 지도와 경로를 조회할 수 있다
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Acceptance Building', description: 'created by Karate acceptance test', latitude: 36.764, longitude: 127.282 }
    When method post
    Then status 201
    * def buildingId = response.buildingId

    Given path 'api', 'v1', 'buildings', buildingId, 'floors'
    And request { name: '1F', level: 1, height: 3.2 }
    When method post
    Then status 201
    * def floorId = response.floorId

    * def scan = support.twoConnectedNodesScan()
    Given path 'api', 'v1', 'floors', floorId, 'scans', 'chunks'
    And multipart file file = { read: '#(scan.zipReadPath)', filename: 'scan.zip', contentType: 'application/zip' }
    When method post
    Then status 201
    And match response.scanId == scan.scanId
    And match response.active == true

    Given path 'api', 'v1', 'floors', floorId, 'process'
    And request {}
    When method post
    Then status 200
    * def buildJobId = response.buildJobId
    * eval support.runBuildJob(buildJobId)

    Given path 'api', 'v1', 'floors', floorId, 'process', 'status'
    When method get
    Then status 200
    And match response.status == 'SUCCEEDED'
    And match response.progress == 1.0

    Given path 'api', 'v1', 'floors', floorId, 'map'
    When method get
    Then status 200
    And match response.nodes == '#[2]'
    And match response.edges == '#[1]'
    And match response.edges[0].type == 'rtabmap_link'

    Given path 'api', 'v1', 'floors', floorId, 'route'
    And param from = scan.startNodeId
    And param to = scan.endNodeId
    When method get
    Then status 200
    And match response.totalDistance == 5.0
    And match response.nodes == '#[2]'
    And match response.edges == '#[1]'
