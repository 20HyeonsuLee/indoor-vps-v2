## rule
- 단위·컴포넌트 테스트는 **Vitest + React Testing Library (RTL)** + **@testing-library/jest-dom**.
- jsdom 환경 (`environment: 'jsdom'`).
- 파일은 production 미러: `Foo.tsx` ↔ `Foo.test.tsx` (같은 폴더).
- RTL 쿼리 우선순위:
  1. `getByRole` (a11y 친화)
  2. `getByLabelText`
  3. `getByPlaceholderText`
  4. `getByText`
  - `getByTestId`는 마지막 수단 (semantic 깨졌을 때)
- 비동기는 `findBy*` 또는 `await waitFor(...)`. `setTimeout` X.
- user 인터랙션은 `@testing-library/user-event` (fireEvent X).
- mock:
  - API 호출: **MSW (Mock Service Worker)** — 권장. fetch intercept
  - 모듈 mock: `vi.mock(...)` (제한적)
  - timer: `vi.useFakeTimers()`
- 컴포넌트 테스트는 사용자 관점 (보이는 것·할 수 있는 것). 구현 detail X.
- TanStack Query 테스트는 새 QueryClient 인스턴스 + `wrapper`.
- Zustand 테스트는 store reset (`useStore.setState(initial, true)`) 또는 fresh import.
- coverage 목표: 핵심 비즈니스 로직 ≥ 80%. UI는 시나리오 중심.

## forbidden
- jest 직접 사용 (Vitest 통일)
- snapshot 남용 (의도 명확한 경우만)
- `getByTestId` 1순위 (semantic 우선)
- `fireEvent` (user-event 사용)
- 구현 detail 테스트 (`state.field === ...` 같은 내부 접근)
- 비동기 wait를 `setTimeout(resolve, 100)`로 (findBy/waitFor)
- 한 테스트에 여러 시나리오
- mock 안 한 채 실제 HTTP 호출 (MSW)
- 컴포넌트 mount 후 assertion 없음
- React act warning 무시
- timer mock 없이 setInterval 의존 테스트
