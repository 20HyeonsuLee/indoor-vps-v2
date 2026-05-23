## rule
- E2E는 **Playwright**. Cypress·Selenium 신규 도입 X.
- 위치: `e2e/<feature>/<scenario>.spec.ts`.
- 시나리오는 사용자 관점 외부 동작 (회원가입·로그인·결제 같은 핵심 flow).
- 셋업은 API 호출로 (DB 직접 X). UI를 통한 시드는 시나리오 노이즈 → API fixture.
- selector 우선순위: `getByRole` > `getByLabel` > `getByText` > `data-testid`.
- assertion은 `expect(...).toBeVisible()` 등 Playwright matchers. 직접 DOM query X.
- network는 `page.route(...)` mock 가능. 일반적으로 실제 dev 서버 호출 권장.
- auth는 `storageState` 저장·재사용 (로그인 1회 후 다른 spec에서 재사용).
- parallel 실행 활성. 단 동일 fixture 의존 시 격리 보장.
- retry: CI에서 2회. 로컬은 0.
- screenshot·video는 실패 시만 (`use: { screenshot: 'only-on-failure', video: 'retain-on-failure' }`).
- trace는 실패 시 자동 (`trace: 'retain-on-failure'`).
- 시간 의존 (현재 시각·random)은 `clock` mock 또는 deterministic 데이터.

## forbidden
- DB 직접 fixture 셋업 (API로)
- selector 1순위가 `data-testid` (role/label 우선)
- `page.waitForTimeout(...)` 고정 대기 (`expect.toBeVisible()` 또는 `waitForResponse`)
- 시나리오 간 상태 의존 (각 spec 독립)
- 한 spec 파일에 무관한 시나리오 묶기
- prod 환경 대상 E2E (dev 또는 staging)
- secret hardcode (env 또는 `.env.local`)
- 매 spec마다 로그인 UI 호출 (storageState 재사용)
- flaky 무시 (3회 연속 실패는 fix 또는 skip + 이슈)
- 시나리오 명에 단순 메서드명 (사용자 관점 동사·결과)
