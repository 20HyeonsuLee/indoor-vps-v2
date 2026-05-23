## rule
- ui/dto는 HTTP 경계의 입출력 타입을 둔다. Request DTO와 Response DTO 두 종류.
- 형태는 `record`. 모든 필드 final.
- Request DTO는 Bean Validation 어노테이션(`@NotBlank`, `@Size`, `@Pattern` 등)으로 **형식 검증**만. 도메인 invariant 검증은 VO 생성자에서.
- Request DTO는 자기 자신을 Command/Query record로 변환하는 메서드를 보유한다 (`toCommand()`). VO 조립도 여기서.
- Response DTO는 UseCase Result를 받아 변환한다 (`Response.from(result)`).
- 명명: `<UseCase>Request`, `<UseCase>Response` 또는 `<Aggregate>Request`/`<Aggregate>Response`.
- DTO는 ui 안에서만 사용. application·domain·infrastructure로 노출 금지.
- Controller 진입점이 비대해지면 mapper class 별도 추출 (`*RequestMapper`).

## forbidden
- domain Entity/VO를 그대로 DTO로 사용
- Request DTO에 도메인 invariant 검증 어노테이션 박기 (형식 검증만)
- Response DTO에 비즈니스 로직 메서드
- DTO를 application Command/Query record와 한 타입으로 통합 (layer 침투)
- DTO에 mutable 필드
- DTO를 entity처럼 영속화 (`@Entity` 부착 금지)
- 다른 context의 DTO import
- 외부 SDK 타입을 DTO 필드로 직접 노출
