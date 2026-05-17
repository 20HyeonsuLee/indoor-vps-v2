@acceptance @e2e
Feature: 스캔 없는 층은 지도 생성을 시작하지 않는다

  Background:
    * url baseUrl

  Scenario: 활성 스캔이 없는 층에서 지도 생성을 요청한다
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

    Given path 'api', 'v1', 'floors', floorId, 'process'
    And request {}
    When method post
    Then status 409
    And match response.code == 'ACTIVE_SCAN_NOT_FOUND'

    Given path 'api', 'v1', 'floors', floorId, 'process', 'status'
    When method get
    Then status 200
    And match response.status == 'IDLE'
    And match response.buildJobId == null
