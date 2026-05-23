## 목적
frontend 기능을 코드로 옮길 때 따르는 절차. **outside-in** — 사용자 시나리오부터 → 컴포넌트 → 상태 → API 순.

## 순서
1. **Playwright E2E spec 작성** (선택, 핵심 사용자 flow일 때)
   - `e2e/<feature>/<scenario>.spec.ts` (red OK)
   - convention: `framework/playwright`
2. **route 정의** (`app/<route>/`)
   - page.tsx + layout.tsx + loading.tsx + error.tsx
   - server component 기본, data fetch는 page에서
   - convention: `architecture/route`, `framework/nextjs`
3. **컴포넌트 작성** (server / client 경계 결정)
   - 라우트 종속: `app/<route>/_components/`
   - 도메인 공통: `src/features/<feature>/components/`
   - 디자인 시스템: `src/components/ui/`
   - convention: `architecture/component`, `framework/react`, `framework/tailwind`
4. **상태 분류 + 도구 선택**
   - server state → TanStack Query (`framework/tanstack-query`)
   - URL state → searchParams
   - form state → React Hook Form + Zod (`framework/react-hook-form`)
   - client state → useState 또는 Zustand (`framework/zustand`)
   - convention: `architecture/store`
5. **API client 작성** (필요 시)
   - `src/features/<feature>/api/<name>.ts`
   - Zod schema + fetch wrapper
   - convention: `architecture/api-client`
6. **custom hook 추출** (재사용 가능 로직)
   - convention: `architecture/hook`
7. **단위·컴포넌트 테스트** (Vitest + RTL)
   - 도메인 로직·hook·중요 컴포넌트
   - convention: `framework/vitest`, `workflows/test`
8. **E2E green 확인** (Playwright)
9. **a11y 점검**
   - 키보드 네비·스크린리더·color contrast
   - `eslint-plugin-jsx-a11y` 자동 + 수동 확인
10. **로깅·메트릭·에러 모니터링**
    - Sentry/PostHog/Datadog (도입 시) + observability/observability

## 분기
- 새 페이지/기능 → `workflows/add-feature.md`
- 새 디자인 시스템 컴포넌트 → `workflows/add-component.md`
- 새 외부 API 통합 → `architecture/api-client` + security 검토
- 디자인 토큰 변경 → `framework/tailwind` (테마 일관성)

## 항상 적용
- 1 PR 1 논리 변경 (`git/pr`)
- Conventional Commits (`git/commit`)
- cleancode·oop (`code/*`)
- secret leak 차단 (`config/env`, `security/security`, `git/gitignore`)

## 완료 기준 (DoD)
- Playwright E2E (있다면) green
- Vitest 단위 테스트 추가/통과
- ESLint + Prettier 통과
- TypeScript strict 통과 (`pnpm typecheck`)
- a11y 점검 (lint + 수동)
- Lighthouse 점수 영향 확인 (LCP·CLS·INP)
- PR description 6 axis 자기 검토 (`git/review`)
