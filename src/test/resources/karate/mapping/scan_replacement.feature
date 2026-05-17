@acceptance @e2e
Feature: 층 스캔 파일을 교체한다

  Background:
    * url baseUrl

  Scenario: 같은 스캔 파일은 명시적 교체 요청이 있을 때만 교체된다
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
    And match response.scanId == scan.scanId
    And match response.active == true

    Given path 'api', 'v1', 'floors', floorId, 'scans', 'chunks'
    And multipart file file = { read: '#(scan.zipReadPath)', filename: 'scan.zip', contentType: 'application/zip' }
    When method post
    Then status 409
    And match response.code == 'SCAN_ALREADY_EXISTS'

    Given path 'api', 'v1', 'floors', floorId, 'scans', 'chunks'
    And param force = 'true'
    And multipart file file = { read: '#(scan.zipReadPath)', filename: 'scan.zip', contentType: 'application/zip' }
    When method post
    Then status 201
    And match response.scanId == scan.scanId
    And match response.active == true
