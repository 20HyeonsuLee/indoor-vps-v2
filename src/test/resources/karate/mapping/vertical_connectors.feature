@acceptance @e2e
Feature: 수직 연결 생성/수정/stop 연결/삭제

  Background:
    * url baseUrl

  Scenario: 컨넥터와 stop 라이프사이클
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Connector Building', description: 'karate', latitude: 36.764, longitude: 127.282 }
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
    And request { x: 1.0, y: 1.0, z: 0.0, nodeType: 'corridor' }
    When method post
    Then status 200
    * def nodeId = response.nodeId

    Given path 'api', 'v1', 'buildings', buildingId, 'connectors'
    And request { connectorType: 'stair', connectorKey: 'S1', name: '본관 계단' }
    When method post
    Then status 200
    And match response.connectorType == 'stair'
    And match response.connectorKey == 'S1'
    And match response.stops == '#[0]'
    * def connectorId = response.connectorId

    Given path 'api', 'v1', 'connectors', connectorId
    And request { name: '본관 계단 1번' }
    When method put
    Then status 200
    And match response.name == '본관 계단 1번'

    Given path 'api', 'v1', 'connectors', connectorId, 'stops'
    And request { areaId: '#(areaId)', routeNodeId: '#(nodeId)' }
    When method post
    Then status 200
    And match response.areaId == areaId
    And match response.routeNodeId == nodeId
    * def stopId = response.stopId

    Given path 'api', 'v1', 'connector-stops', stopId
    And request { detachRouteNode: true }
    When method put
    Then status 200
    And match response.routeNodeId == null

    Given path 'api', 'v1', 'connector-stops', stopId
    When method delete
    Then status 204

    Given path 'api', 'v1', 'connectors', connectorId
    When method delete
    Then status 204
