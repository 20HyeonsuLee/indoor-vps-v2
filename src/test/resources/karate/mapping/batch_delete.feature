@acceptance @e2e
Feature: 건물 일괄 삭제

  Background:
    * url baseUrl

  Scenario: 여러 건물을 한 번의 요청으로 삭제한다
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Batch A', description: 'karate', latitude: 36.764, longitude: 127.282 }
    When method post
    Then status 201
    * def idA = response.buildingId

    Given path 'api', 'v1', 'buildings'
    And request { name: 'Batch B', description: 'karate', latitude: 36.765, longitude: 127.283 }
    When method post
    Then status 201
    * def idB = response.buildingId

    Given path 'api', 'v1', 'buildings', 'batch'
    And request ['#(idA)', '#(idB)']
    When method delete
    Then status 204

    Given path 'api', 'v1', 'buildings', idA
    When method get
    Then status 404

    Given path 'api', 'v1', 'buildings', idB
    When method get
    Then status 404
