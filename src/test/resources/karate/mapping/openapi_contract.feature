@acceptance @e2e
Feature: OpenAPI 명세 표면과 호환성 alias가 규격대로 동작한다

  Background:
    * url baseUrl

  Scenario: /openapi.json 은 기대 경로 집합을 모두 포함한다
    Given path 'openapi.json'
    When method get
    Then status 200
    And match response.paths contains
    """
    {
      '/api/slam/v3/localize': '#present',
      '/api/v1/buildings': '#present',
      '/api/v1/buildings/{buildingId}': '#present',
      '/api/v1/buildings/{buildingId}/floors': '#present',
      '/api/v1/buildings/{buildingId}/pathfinding': '#present',
      '/api/v1/buildings/{buildingId}/passages': '#present',
      '/api/v1/buildings/{buildingId}/pois': '#present',
      '/api/v1/buildings/{buildingId}/pois/search': '#present',
      '/api/v1/buildings/{buildingId}/status': '#present',
      '/api/v1/floors/{floorId}': '#present',
      '/api/v1/floors/{floorId}/map': '#present',
      '/api/v1/floors/{floorId}/path': '#present',
      '/api/v1/floors/{floorId}/build': '#present',
      '/api/v1/floors/{floorId}/process': '#present',
      '/api/v1/floors/{floorId}/process/status': '#present',
      '/api/v1/floors/{floorId}/route': '#present',
      '/api/v1/floors/{floorId}/scans/start': '#present',
      '/api/v1/floors/{floorId}/scans/chunks': '#present',
      '/api/v1/floors/{floorId}/scans/chunks/{chunkId}': '#present',
      '/api/v1/floors/{floorId}/scans/merge': '#present',
      '/api/v1/floors/{floorId}/scans/merge/status': '#present',
      '/api/v1/scans/{scanId}/frames': '#present',
      '/api/v1/scans/{scanId}/finalize': '#present'
    }
    """

  Scenario: /docs 는 Swagger UI HTML 을 200 으로 반환한다
    Given path 'docs'
    When method get
    Then status 200
    And match responseHeaders['Content-Type'][0] contains 'text/html'

  Scenario: /swagger-ui/index.html 는 Swagger UI HTML 을 200 으로 반환한다
    Given path 'swagger-ui', 'index.html'
    When method get
    Then status 200
    And match responseHeaders['Content-Type'][0] contains 'text/html'

  Scenario: Python bridge 비활성 상태에서 localize 요청은 503 오류를 반환한다
    Given path 'api', 'slam', 'v3', 'localize'
    And multipart file images = { read: 'classpath:karate/fixtures/fake_frame.jpg', filename: 'frame.jpg', contentType: 'image/jpeg' }
    And multipart field building_id = 'building-1'
    When method post
    Then status 503
    And match response.code == 'PYTHON_BRIDGE_DISABLED'
