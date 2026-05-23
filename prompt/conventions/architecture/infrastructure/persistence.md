## rule
- infrastructure/persistence는 domain에 선언된 Repository의 **Custom 구현체**를 둔다. 기본 Spring Data 자동 생성은 구현 파일 없음.
- 명명은 `<Aggregate>RepositoryCustomImpl`. domain에 같이 선언된 `<Aggregate>RepositoryCustom` interface를 구현한다.
- 복잡 JPQL/Native/QueryDSL이 필요할 때 한정 사용. 단순 CRUD는 Spring Data 메서드명 규약으로 충분.
- 외부 산출물(RTAB-Map SQLite 등) 읽기는 JdbcClient 직접 사용 허용 (ADR-010). 단 read-only adapter에 한정.
- 외부 산출물 어댑터는 `infrastructure/<system>/`(예: `rtabmap/`)에 둔다. persistence/는 앱 DataSource(PostgreSQL) 한정.
- 영속화 부수 처리(트랜잭션 로그, 캐시 무효화 등)도 여기에 둘 수 있으나, 비즈니스 규칙은 금지.

## forbidden
- domain Repository interface를 여기서 재선언 (domain에 한 번만 선언)
- 비즈니스 로직 (변환·쿼리만)
- 앱 DataSource를 우회한 직접 JDBC (외부 산출물 외)
- Aggregate 경계 넘는 쿼리 (다른 Aggregate fetch join 금지 — domain/repository.md 참조)
- 외부 산출물 쓰기 작업 (read-only adapter만)
- QueryDSL 도입 (ADR-007 — 별도 ADR 필요)
- 다른 Bounded Context의 domain import
