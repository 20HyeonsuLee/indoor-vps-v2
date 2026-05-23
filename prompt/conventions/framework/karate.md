## rule
- Karate feature 위치: `src/test/resources/karate/<context>/<usecase>.feature`. context는 도메인 context명과 일치.
- 파일명은 `<usecase_snake_case>.feature` (UseCase 명 lowercase snake).
- runner 클래스는 `src/test/java/.../karate/<Context>KarateTest.java` 또는 단일 `KarateRunnerTest`.
- BDD 작성 순서(CLAUDE.md `_project_meta.coding_principles.bdd`): **Karate feature → domain → UseCase → Repository → Controller** (outside-in).
- feature 안에 비즈니스 로직 금지. 외부 동작 명세만.
- fixture/사전 상태는 **API 호출로 셋업**. DB 직접 INSERT, 외부 파일 fixture loader 금지.
- 인증/세션 같은 공통 셋업은 `Background:` 블록 또는 `karate-config.js` 전역 함수.
- assertion은 schema 검증 우선 (`match response == { id: '#string', ... }`). 값 비교는 핵심 필드만.
- 외부 의존(외부 API·Python adapter)은 feature가 stub 안 함. 실제 호출 또는 spring profile로 mock(단 mock은 architecture/code 정책 위반에 가까움 — 정 필요하면 ADR).
- timeout은 `* configure connectTimeout = 5000` 등 feature 또는 config에 명시.
- 시나리오 명은 한글 가능. `Scenario: 빌딩 생성 후 floor 조회 시 polygon 포함`.
- 재현성: 시나리오 내 시간/uuid 의존은 변수화 (`* def now = ...`).
- @ignore / @smoke / @e2e 같은 tag로 실행 분류. CI에서 tag 기반 분기.

## forbidden
- feature 안 비즈니스 로직 (조건 분기·계산)
- DB·파일 직접 fixture 셋업 (API로 셋업)
- 한 feature에 무관한 시나리오 묶기
- assertion 누락 (request만 보내고 응답 검증 X)
- `match response contains '*'` 같은 모호한 검증
- 시나리오 간 상태 의존 (각 시나리오 독립)
- 외부 stub을 feature 안에 inline 작성 (config 또는 별도 fixture endpoint)
- `@ignore` 누적 (skip 사유 + 해결 책임자 명시 필요)
- 절대 시간 hardcode (`'2025-01-01T00:00:00'`)
- credentials hardcode (config 또는 env)
- 한 feature 파일 > 200줄 (분리)
- `@SpringBootTest` 단위 통합 테스트로 Karate 대체 (CLAUDE.md `_project_meta` — Karate가 통합테스트 겸함)
