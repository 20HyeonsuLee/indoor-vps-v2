## rule
- Repository 인터페이스는 domain에 선언한다. Spring Data `JpaRepository`를 extends 한다.
- 1 Aggregate Root = 1 Repository. Aggregate 내부 Entity 별도 Repository 만들지 않는다.
- 명명은 `<Aggregate>Repository` (예: `BuildingRepository`, `FloorRepository`).
- 메서드 명명은 Spring Data 쿼리 메서드 규약(`findByXAndY`, `existsByZ`)을 따른다.
- 반환은 Optional, List, Page. 단건 조회 미존재 시 Optional.empty.
- 복잡 JPQL/Native/QueryDSL이 필요하면 `<Aggregate>RepositoryCustom` interface를 domain에 추가 선언하고, 구현체는 `infrastructure/persistence/<Aggregate>RepositoryCustomImpl`에 둔다.
- Repository는 Aggregate 단위로 영속화한다. 여러 Aggregate를 한 메서드에서 변경 금지.

## forbidden
- 별도 Repository 구현 클래스 작성 (Spring Data 자동 생성 사용. Custom 패턴은 예외)
- Aggregate Root 외 Entity별 Repository
- Repository 메서드에 비즈니스 규칙 내장 (예: `findActiveBuildingsAndDeactivateOld`)
- null 반환 (Optional 사용)
- 다른 Aggregate fetch join (Aggregate 경계 위반 — 필요하면 ID로 별도 조회)
- QueryDSL/JdbcTemplate 도입
- 다른 context의 Repository import
