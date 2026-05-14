# language: ko
@acceptance @e2e
기능: 잘못된 요청은 표준 오류 응답으로 거절된다

  시나리오: 건물 식별자가 UUID가 아니면 검증 오류를 받는다
    만약 잘못된 건물 식별자로 건물을 조회한다
    그러면 요청은 "VALIDATION_ERROR" 오류로 거절된다
