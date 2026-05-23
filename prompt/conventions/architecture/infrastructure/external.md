## rule
- infrastructure/external은 외부 REST/gRPC/SDK 시스템 클라이언트를 둔다.
- domain 또는 application에 선언된 port를 구현한다. 명명은 `*Client`.
- 외부 SDK·라이브러리 타입은 어댑터 내부에 격리. 외부에는 도메인 친화 record로 변환해 노출.
- HTTP 클라이언트는 RestClient/WebClient 기반. 재시도·타임아웃·circuit breaker는 어댑터 또는 별도 정책 클래스에서.
- 외부 응답 에러는 도메인 예외 또는 application 예외로 매핑. 외부 라이브러리 예외 그대로 throw 금지.
- 시스템별 sub-folder 분리 (`infrastructure/external/<system>/`).

## forbidden
- domain/application port 없이 외부 시스템 직접 호출
- 비즈니스 로직 (변환·호출만)
- 외부 SDK 타입을 도메인/application 시그니처에 노출
- credentials 하드코딩 (config 또는 secret manager 사용)
- 무한 timeout (명시 필수)
- 외부 라이브러리 예외 그대로 propagate
- 다른 Bounded Context의 domain import
- application의 다른 모듈에 직접 의존 (port 통해서만)
