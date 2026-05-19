@acceptance @e2e
Feature: Area 폴리곤(코너) 생성/vertex 교체/삭제

  Background:
    * url baseUrl

  Scenario: 폴리곤 라이프사이클
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Polygon Building', description: 'karate', latitude: 36.764, longitude: 127.282 }
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

    Given path 'api', 'v1', 'areas', areaId, 'polygons'
    And request { exterior: [{ x: 0.0, y: 0.0, z: 0.0 }, { x: 1.0, y: 0.0, z: 0.0 }, { x: 1.0, y: 1.0, z: 0.0 }, { x: 0.0, y: 1.0, z: 0.0 }] }
    When method post
    Then status 200
    * def polygonId = response.polygonId

    Given path 'api', 'v1', 'areas', areaId, 'polygons'
    When method get
    Then status 200
    And match response == '#[_ > 0]'

    Given path 'api', 'v1', 'polygons', polygonId
    And request { exterior: [{ x: 0.0, y: 0.0, z: 0.0 }, { x: 2.0, y: 0.0, z: 0.0 }, { x: 2.0, y: 2.0, z: 0.0 }, { x: 0.0, y: 2.0, z: 0.0 }] }
    When method put
    Then status 200

    Given path 'api', 'v1', 'polygons', polygonId
    When method delete
    Then status 204
