@acceptance @e2e
Feature: 생성된 층 지도는 캐시 검증으로 재사용된다

  Background:
    * url baseUrl

  Scenario: 같은 지도 버전을 다시 조회하면 본문을 내려받지 않는다
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

    Given path 'api', 'v1', 'floors', floorId, 'process'
    And request {}
    When method post
    Then status 200
    * eval support.runBuildJob(response.buildJobId)

    Given path 'api', 'v1', 'floors', floorId, 'map'
    When method get
    Then status 200
    * def etag = responseHeaders['ETag'][0]
    * match etag != ''

    Given path 'api', 'v1', 'floors', floorId, 'map'
    And header If-None-Match = etag
    When method get
    Then status 304
    And match response == null
