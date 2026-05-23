## rule
- Domain Service는 여러 domain 객체가 협력하는 비즈니스 규칙을 담는다. 한 Aggregate Root 안에 못 담을 때 한정 사용한다.
- 도입 전에 Aggregate Root에 행위 부여로 해결할 수 있는지 먼저 검토한다. Domain Service는 마지막 수단.
- stateless. 인스턴스 변수 없음. `@Component` 또는 plain class.
- Application UseCase와 책임 경계:
  - Domain Service: 순수 도메인 규칙. 트랜잭션·외부 호출 없음.
  - Application UseCase: 트랜잭션 시작·외부 호출·여러 Domain Service/Aggregate 조율.
- 명명은 `<도메인동사>` UseCase와 혼동 방지를 위해 `*Service` 단독 명명은 지양.
- 같은 context의 domain 객체만 의존한다.

## forbidden
- 상태(인스턴스 변수) 보유
- 외부 시스템 직접 호출 (port 필요하면 application 경유)
- 다른 Bounded Context의 domain/service import
- 트랜잭션 시작 (`@Transactional`은 UseCase에만)
- getter로 도메인 객체 상태 꺼내 분기/계산만 하는 anemic service
- application/infrastructure import
- Domain Event 발행
