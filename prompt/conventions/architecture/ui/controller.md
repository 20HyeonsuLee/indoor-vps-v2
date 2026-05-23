## rule
- Controller는 HTTP 요청을 받아 Request DTO로 바인딩 → Command/Query record로 변환 → UseCase 호출 → Result → Response DTO 반환의 흐름만 수행한다.
- 명명은 `<Aggregate>Controller` 또는 `<UseCase 묶음>Controller`. 1 Controller = 1 Aggregate 또는 1 sub-domain 묶음.
- 진입점 메서드는 `@GetMapping`/`@PostMapping` 등 HTTP 메서드 어노테이션으로 명시.
- Request DTO → Command 변환은 `RequestDto.toCommand()` 또는 별도 mapper. Controller 안 1줄로 끝.
- 응답은 Response DTO 또는 `ResponseEntity<Response>`. domain Entity 직접 반환 금지.
- API 명세는 OpenAPI(Swagger) annotation 또는 `*ApiSpec` interface로 분리 가능. (선택)
- 예외는 던지고 잡지 않는다. GlobalExceptionHandler가 매핑한다.
- 한 진입점에서 다수 UseCase 호출은 허용 (한 화면 합성). 단 트랜잭션 묶음이면 application으로 끌어올림.

## forbidden
- 비즈니스 로직 (UseCase에 위임)
- Repository 직접 호출 (반드시 UseCase 경유)
- domain Entity/VO 직접 응답 노출 (Response DTO로 변환)
- Controller에서 도메인 예외 `try/catch` (GlobalExceptionHandler 위임)
- 다른 context의 Controller 직접 호출 (cross-context는 application 책임)
- Request DTO 없이 primitive 다중 인자 (의미 단위 포장)
- null 반환 (빈 컬렉션 또는 404)
- session/cookie에 도메인 상태 저장 (stateless)
- 한 Controller에 무관한 Aggregate 묶기 (god controller 금지)
