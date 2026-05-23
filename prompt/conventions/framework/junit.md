## rule
- 단위 테스트는 **JUnit5 + AssertJ**. Hamcrest·legacy JUnit4 금지.
- 테스트 클래스는 production 패키지 미러. 명명은 `<TargetClass>Test`.
- 한 테스트 메서드 = 한 시나리오. 한 assertion 묶음.
- 메서드 명명은 한글 + `_` 또는 영문 BDD (`should_return_404_when_floor_not_found` 또는 `floor_없으면_404_반환`). `@DisplayName`으로 한글 시나리오 보강.
- 구조는 **given / when / then** 주석으로 분리. 또는 빈 줄로.
- 모킹 최소. **외부 경계(adapter, port)만 mock**. domain 객체와 application UseCase는 실제 객체 사용.
- 모킹 라이브러리는 Mockito 한 가지. PowerMock·Spock 금지.
- parameterized 테스트는 `@ParameterizedTest` + `@MethodSource` 또는 `@CsvSource`. 같은 시나리오의 입력 변형에 한정.
- AssertJ: `assertThat(actual).isEqualTo(expected)`. 체이닝 적극 (`.hasSize(3).contains(x, y)`).
- Karate가 통합 테스트 겸함(ADR-004). `@SpringBootTest` 단위 통합 신규 작성 금지.
- 도메인 객체 단위 테스트는 JPA 부착에도 불구하고 plain JUnit으로 가능(생성·invariant 검증).
- 테스트는 독립적. 한 테스트 결과가 다른 테스트에 영향 X. 공유 상태 금지.
- 외부 자원(DB, 파일, 네트워크) 직접 의존 금지. 필요 시 port mock 또는 in-memory.
- 테스트 1개 실행 시간 < 100ms 권장. 초과 시 Karate로 이동 검토.

## forbidden
- Hamcrest, JUnit4 (JUnit5 + AssertJ 통일)
- `@SpringBootTest` 신규 통합 (Karate가 대체)
- 도메인 객체 mock (실제 객체 사용)
- 한 테스트에 여러 시나리오 (한 테스트 한 시나리오)
- 시나리오 명에 단순 메서드명만 (`createFloor_test`) — BDD 동사 또는 한글 시나리오
- assertion 누락 (action만 호출하고 검증 X)
- 테스트 간 공유 상태 (`static` 변경 가능 필드, 순서 의존)
- Thread.sleep·실제 시간 의존 (`Clock` mock 또는 time provider)
- PowerMock, Spock 도입
- `@Disabled` 누적 (이유 + 해결 책임자 명시)
- random seed 없이 random 의존 (재현 불가)
- 다른 테스트의 setup에 implicit 의존
