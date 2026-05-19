@acceptance @e2e
Feature: PLY 포인트클라우드 스트리밍

  Background:
    * url baseUrl

  Scenario: PLY가 아직 export 되지 않은 경우 404 응답
    # python.enabled=false라 BuildJobRunner는 cloud.ply를 export하지 않는다.
    # PointcloudController는 storage_path 디렉터리의 cloud.ply 부재로 404를 반환한다.
    Given path 'api', 'v1', 'buildings'
    And request { name: 'Pointcloud Building', description: 'karate', latitude: 36.764, longitude: 127.282 }
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

    Given path 'api', 'v1', 'floors', floorId, 'pointcloud'
    When method get
    Then status 404
    And match response.code == 'POINTCLOUD_NOT_AVAILABLE'

    Given path 'api', 'v1', 'areas', areaId, 'pointcloud'
    When method get
    Then status 404
    And match response.code == 'POINTCLOUD_NOT_AVAILABLE'
