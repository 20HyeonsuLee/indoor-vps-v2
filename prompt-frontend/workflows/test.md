## 목적
프론트 테스트 작성·실행·관리 절차. 단위(Vitest) + E2E(Playwright) 2층.

## 테스트 층
| 층 | 도구 | 위치 | 책임 |
|---|---|---|---|
| 단위·컴포넌트 | Vitest + RTL + jest-dom | `<file>.test.tsx` (같은 폴더) | hook·util·도메인 로직·컴포넌트 단위 시나리오 |
| E2E | Playwright | `e2e/<feature>/<scenario>.spec.ts` | 사용자 핵심 flow (로그인·결제·검색) |

## 작성 순서 (outside-in)
1. Playwright spec 작성 (핵심 flow) — red OK
2. 컴포넌트·hook 단위 테스트 (도메인 로직 우선)
3. 구현 → 둘 다 green

## 단위 테스트 룰 (framework/vitest)
- 사용자 관점 (보이는 것·할 수 있는 것). 구현 detail X
- query 우선: getByRole > getByLabel > getByText > getByTestId
- 비동기: findBy/waitFor (setTimeout X)
- 인터랙션: user-event (fireEvent X)
- mock: MSW로 API, vi.mock으로 모듈
- TanStack Query: 새 QueryClient + wrapper
- Zustand: store reset

## E2E 룰 (framework/playwright)
- 시나리오는 사용자 핵심 flow
- 셋업은 API로 (DB 직접 X)
- selector role 우선
- auth는 storageState 재사용
- 실패 시만 screenshot/video/trace
- flaky 무시 X

## 실행
- 단위: `pnpm test` (또는 `pnpm vitest`)
- E2E: `pnpm test:e2e` (또는 `pnpm playwright test`)
- 둘 다: `pnpm test:all` (CI)
- coverage: `pnpm test --coverage`

## CI 게이트
- PR: 단위 + E2E 모두 green 필수 (`workflows/cicd`)
- TypeScript strict 통과
- ESLint 통과
- a11y lint 통과
- fail 시 PR merge X

## 변경별 테스트 동반
| 변경 | 동반 테스트 |
|---|---|
| 새 컴포넌트 | 컴포넌트 단위 테스트 + (핵심이면) E2E 시나리오 |
| 새 hook | hook 단위 테스트 (renderHook) |
| 새 API 통합 | MSW 기반 mock + 컴포넌트 통합 테스트 |
| 새 페이지 (route) | Playwright 시나리오 1+ |
| 새 form | RHF + Zod schema 테스트 + 제출 시나리오 |
| bug fix | 회귀 테스트 1개 (재발 방지) |
| 디자인 토큰 변경 | visual regression (Chromatic/Percy 도입 시) 또는 수동 점검 |
| a11y 영향 | jest-axe / axe-playwright |

## 안티패턴
- assertion 없는 테스트
- 구현 detail 테스트 (state 직접 비교)
- setTimeout 대기
- `getByTestId` 1순위 사용
- 실제 HTTP 호출 (MSW 사용)
- E2E에서 DB 직접 셋업
- snapshot 남용
- React act warning 무시
