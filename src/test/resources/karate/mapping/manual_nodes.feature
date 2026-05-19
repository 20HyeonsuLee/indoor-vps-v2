@acceptance @e2e
Feature: 관리자가 스캔 위에 수동 노드를 추가/수정/삭제할 수 있다

  Background:
    * url baseUrl

  Scenario: 노드 생성/이동/타입변경/삭제가 영속화된다
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Manual Node Building', description: 'karate', latitude: 36.764, longitude: 127.282 }
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
    And match response == '#[_ > 0]'
    * def areaId = response[0].areaId

    Given path 'api', 'v1', 'areas', areaId, 'nodes'
    And request { x: 1.0, y: 2.0, z: 0.5, nodeType: 'corridor', label: 'manual A' }
    When method post
    Then status 200
    And match response.areaId == areaId
    And match response.nodeType == 'corridor'
    And match response.label == 'manual A'
    And match response.origin == 'manual_edit'
    * def nodeId = response.nodeId

    Given path 'api', 'v1', 'nodes', nodeId
    And request { x: 1.5, y: 2.5, z: 0.5, label: 'manual A2', nodeType: 'junction' }
    When method put
    Then status 200
    And match response.x == 1.5
    And match response.y == 2.5
    And match response.nodeType == 'junction'
    And match response.label == 'manual A2'

    Given path 'api', 'v1', 'nodes', nodeId
    When method delete
    Then status 204
