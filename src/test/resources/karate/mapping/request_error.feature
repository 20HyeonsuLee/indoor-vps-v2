@acceptance @e2e
Feature: 잘못된 요청은 표준 오류 응답으로 거절된다

  Background:
    * url baseUrl

  Scenario: 건물 식별자가 UUID가 아니면 검증 오류를 받는다
    Given path 'api', 'v1', 'buildings', 'not-a-uuid'
    When method get
    Then status 422
    And match response.code == 'VALIDATION_ERROR'
