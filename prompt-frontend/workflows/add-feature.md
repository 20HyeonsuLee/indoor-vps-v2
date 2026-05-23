## 목적
사용자 기능 단위(feature)를 frontend에 추가하는 절차. 라우트 + 컴포넌트 + 상태 + API + 테스트 한 묶음.

## feature 폴더 구조
```
src/features/<feature>/
  api/
    queries.ts          # TanStack Query
    mutations.ts
    schema.ts           # Zod
  components/
    <Name>.tsx
  hooks/
    use<Name>.ts
  types.ts              # 공유 타입
  index.ts              # public API (선택)
```

## 절차
1. **이슈 + 시나리오 명세**
   - feature 이슈 발급 (`workflows/task-flow` step 1)
   - 사용자 시나리오 3~5개 (PRD에 인용)
2. **Zod schema 정의** (`api/schema.ts`)
   - 서버 응답·요청·form input의 타입 SSOT
3. **API client + query/mutation** (`api/queries.ts`, `api/mutations.ts`)
   - convention: `architecture/api-client`, `framework/tanstack-query`
   - query key factory
4. **route 추가** (`app/<route>/`)
   - page.tsx + loading.tsx + error.tsx
   - data fetch는 server component (prefetch + HydrationBoundary)
   - convention: `architecture/route`, `framework/nextjs`
5. **컴포넌트 작성** (`components/`)
   - server / client 경계 결정
   - convention: `architecture/component`
6. **hook 추출** (재사용 가능 시)
   - convention: `architecture/hook`
7. **form (있다면)** RHF + Zod
   - convention: `framework/react-hook-form`
8. **state 분류** (server/URL/form/client)
   - convention: `architecture/store`
9. **테스트**
   - 단위·컴포넌트: Vitest + RTL
   - E2E: Playwright 시나리오 1+
   - convention: `workflows/test`, `framework/vitest`, `framework/playwright`
10. **a11y + 성능**
    - axe·Lighthouse
    - LCP·CLS·INP 영향 점검
11. **observability**
    - Sentry/PostHog event (도입 시)
    - convention: `observability/observability`
12. **PR open** (`workflows/code-review`)

## 금지
- feature 폴더 외부에 도메인 종속 코드 산발 (응집도)
- API schema 없이 ad-hoc fetch
- 한 feature가 여러 도메인 책임 (분리)
- 라우트 page에 `"use client"` (server component 활용)
- form state를 useState로 관리 (RHF 사용)
- server state를 Zustand에 (TanStack Query 사용)
- a11y 미점검 PR
- Lighthouse 영향 측정 누락 (큰 컴포넌트 추가 시)
