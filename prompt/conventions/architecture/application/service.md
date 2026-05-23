## rule
- Application Service = UseCase. 1 UseCase = 1 클래스.
- 명명은 `<동사><명사>UseCase` (`CreateBuildingUseCase`, `RegisterScanUseCase`, `BindFloorPolygonUseCase`). `*Service` 단독 명명은 Domain Service와 혼동 방지를 위해 지양.
- 트랜잭션 경계 = UseCase 메서드. `@Transactional`은 여기에만.
- public 진입 메서드 1개 권장(`execute`, `handle`, 또는 동사). 진입점 늘어나면 UseCase 분리.
- 입력은 Command record(쓰기) 또는 Query record(읽기). 반환은 Result record 또는 단일 VO/ID.
- Command/Query/Result는 UseCase 안 inner record 우선(UseCase 시그니처의 일부). UseCase 파일이 비대해지면 같은 폴더 별도 파일로 분리. cross-UseCase 재사용이 진짜 발생할 때만 `application/<sub-domain>/common/` 또는 `application/dto/`로 추출.
- Command/Query 필드는 VO 우선. ui Request DTO의 원시값 → VO 조립은 ui Controller 책임 (또는 RequestDto.toCommand()). UseCase는 VO 받아 조율만.
- 같은 context의 domain 객체와 port를 사용한다. 외부 시스템 호출은 port 통해서만.
- 다른 Bounded Context는 그 context의 UseCase만 호출. domain/infrastructure import 금지.
- 비즈니스 규칙은 도메인 객체에 위임. UseCase는 조율(load → 행위 호출 → save → 외부 호출)만.
- 한 UseCase 안에서 여러 Aggregate를 변경해야 하면 각 Aggregate의 변경은 별도 트랜잭션으로 분리하거나 saga 패턴 검토. (한 트랜잭션 = 한 Aggregate)
- UseCase 수 > 15 이면 응집도별 sub-folder로 분리(`application/scan/`, `application/floor/`).

## forbidden
- `*Service` 단독 명명 (Domain Service와 혼동)
- UseCase가 다른 UseCase 호출 (cross-context 제외)
- Repository에 비즈니스 규칙 위임
- getter로 도메인 상태 꺼내 분기/계산
- Domain Event 발행 (ADR-002)
- Aggregate 경계 넘는 단일 트랜잭션
- domain Entity/VO를 그대로 반환 (Result record로 변환)
- 다른 context의 domain/infrastructure import
- null 반환·전달 (Optional 또는 Null Object)
- public 진입 메서드 2개 이상 (UseCase 분리)
