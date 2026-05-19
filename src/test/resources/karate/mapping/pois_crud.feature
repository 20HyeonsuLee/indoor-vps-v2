@acceptance @e2e
Feature: POI 생성/이름변경/노드연결/삭제

  Background:
    * url baseUrl

  Scenario: POI 라이프사이클
    Given path 'api', 'v1', 'buildings'
    And request { name: 'POI Building', description: 'karate', latitude: 36.764, longitude: 127.282 }
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
    And request { x: 1.0, y: 1.0, z: 0.0, nodeType: 'poi_attach', label: 'spot' }
    When method post
    Then status 200
    * def nodeId = response.nodeId

    Given path 'api', 'v1', 'buildings', buildingId, 'pois'
    And request { areaId: '#(areaId)', name: '문', category: 'door', x: 1.0, y: 1.0, z: 0.0 }
    When method post
    Then status 200
    And match response.name == '문'
    And match response.category == 'door'
    * def poiId = response.poiId

    Given path 'api', 'v1', 'pois', poiId
    And request { name: '정문', category: 'entrance' }
    When method put
    Then status 200
    And match response.name == '정문'
    And match response.category == 'entrance'

    Given path 'api', 'v1', 'pois', poiId, 'route-node'
    And request { routeNodeId: '#(nodeId)' }
    When method put
    Then status 200
    And match response.routeNodeId == nodeId

    Given path 'api', 'v1', 'pois', poiId
    When method delete
    Then status 204
