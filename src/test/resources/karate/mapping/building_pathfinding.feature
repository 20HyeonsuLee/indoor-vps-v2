@acceptance @e2e
Feature: 건물 길찾기는 목적지 검색 실패를 결과로 알려준다

  Background:
    * url baseUrl

  Scenario: 존재하지 않는 목적지를 요청한다
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Acceptance Building', description: 'created by Karate acceptance test', latitude: 36.764, longitude: 127.282 }
    When method post
    Then status 201
    * def buildingId = response.buildingId

    Given path 'api', 'v1', 'buildings', buildingId, 'pathfinding'
    And request { startFloorLevel: 1, startX: 0.0, startY: 0.0, startZ: 0.0, destinationName: '없는 목적지' }
    When method post
    Then status 200
    And match response.routeMetadata.destinationFound == false
    And match response.steps == '#[1]'
    And match response.steps[0].instruction == 'Start'
    And match response.totalDistance == 0.0
