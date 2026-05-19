@acceptance @e2e
Feature: 수동 노드 두 개를 연결하는 엣지를 추가/수정/삭제할 수 있다

  Background:
    * url baseUrl

  Scenario: 두 수동 노드 사이에 엣지를 잇고 타입을 변경한다
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Manual Edge Building', description: 'karate', latitude: 36.764, longitude: 127.282 }
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

    Given path 'api', 'v1', 'floors', floorId, 'areas'
    When method get
    Then status 200
    * def areaId = response[0].areaId

    Given path 'api', 'v1', 'areas', areaId, 'nodes'
    And request { x: 0.0, y: 0.0, z: 0.0, nodeType: 'corridor' }
    When method post
    Then status 200
    * def fromId = response.nodeId

    Given path 'api', 'v1', 'areas', areaId, 'nodes'
    And request { x: 3.0, y: 0.0, z: 0.0, nodeType: 'corridor' }
    When method post
    Then status 200
    * def toId = response.nodeId

    Given path 'api', 'v1', 'areas', areaId, 'edges'
    And request { fromNodeId: '#(fromId)', toNodeId: '#(toId)', edgeType: 'rtabmap_link' }
    When method post
    Then status 200
    And match response.fromNodeId == fromId
    And match response.toNodeId == toId
    And match response.edgeType == 'rtabmap_link'
    And match response.lengthM == 3.0
    * def edgeId = response.edgeId

    Given path 'api', 'v1', 'edges', edgeId
    And request { edgeType: 'poi_spur' }
    When method put
    Then status 200
    And match response.edgeType == 'poi_spur'

    Given path 'api', 'v1', 'edges', edgeId
    When method delete
    Then status 204
