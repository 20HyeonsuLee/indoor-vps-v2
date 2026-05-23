# AI Agent 인덱스 루트 (Frontend)

Stack: **Next.js 15 App Router + TypeScript + Tailwind + TanStack Query + Zustand + React Hook Form/Zod + Vitest + Playwright + pnpm**

- **`index.yml`**: 프로젝트 구조 + 경로별 `_conventions`/`_workflows`.
- **`CLAUDE.md`** (이 파일): 에이전트 유형별 진입 룰 + 동적 로드 매트릭스.

로드 순서: 루트 `CLAUDE.md` → `prompt-frontend/index.yml` → `prompt-frontend/CLAUDE.md` → 변경 경로의 `_conventions`/`_workflows` 동적.

---

## 에이전트 유형

| 유형 | 단계 | 책임 | 산출물 | 핵심 참조 |
|---|---|---|---|---|
| plan | 1 | 설계·결정 | ADR/PRD | `docs/ard`, `docs/prd`, `docs/glossary`, `architecture/*` |
| eval-plan | 1.5 | plan 검토 | review 코멘트 | `architecture/*`, 관련 ADR, `workflows/code-review` |
| generate-plan | 2 | 구현 분할 | `docs/tasks/<seq>/plan.md` | `architecture/*`, `git/pr`, `workflows/code`, 분기 workflow |
| generate | 3 | 코드 작성 | PR | `code/*`, 경로별 동적 로드 (아래), `workflows/code` |
| eval | 4 | PR 검토 | review 코멘트 | `git/review`, `workflows/code-review`, 경로별 동적 로드 |

**6 axis review**: 도메인 일관성 · 테스트 · convention · UI/a11y · secret · observability/perf

---

## 동적 로드 매트릭스 (변경 경로 → 추가 로드)

| 변경 패턴 | 로드 |
|---|---|
| `app/**/page.tsx` | `architecture/route`, `framework/nextjs`, `framework/react` |
| `app/**/layout.tsx` | `architecture/route`, `framework/nextjs` |
| `app/**/error.tsx` / `loading.tsx` / `not-found.tsx` | `architecture/route`, `observability/observability` |
| `app/api/**/route.ts` | `architecture/api-client`, `framework/nextjs`, `security/security` |
| `app/**/_components/**` | `architecture/component`, `framework/react`, `framework/tailwind` |
| `src/components/ui/**` | `architecture/component`, `framework/tailwind`, `workflows/add-component` |
| `src/features/<f>/components/**` | `architecture/component`, `framework/react`, `framework/tailwind` |
| `src/features/<f>/hooks/**` | `architecture/hook`, `framework/react` |
| `src/features/<f>/api/**` | `architecture/api-client`, `framework/tanstack-query` |
| `src/stores/**` | `architecture/store`, `framework/zustand` |
| `src/lib/api/**` | `architecture/api-client` |
| `*.test.{ts,tsx}` | `framework/vitest`, `workflows/test` |
| `e2e/**` | `framework/playwright`, `workflows/test` |
| `next.config.{ts,mjs,js}` | `build/next-config`, `framework/nextjs` |
| `tailwind.config.{ts,js}` | `framework/tailwind` |
| `tsconfig.json` | `config/tsconfig` |
| `eslint.config.{ts,js}` / `.prettierrc` | `config/eslint-prettier` |
| `.env*` | `config/env`, `security/security` |
| `package.json` / `pnpm-lock.yaml` | `build/pnpm` |
| `docker/**`, `Dockerfile*`, `docker-compose*.yml` | `docker/*`, `workflows/cicd` |
| `docs/decisions/**` | `docs/ard` |
| `.github/workflows/**` | `workflows/cicd`, `docker/image`, `git/release` |
| `.gitignore` / `.gitattributes` | `git/gitignore` / `git/gitattributes` |
| **모든 PR 공통** | `code/cleancode`, `code/oop`, `git/commit`, `git/pr`, `git/review` |

---

## 작업 종류 → workflow

**모든 작업의 entry는 `workflows/task-flow`** (이슈 티켓 발급부터).

| 작업 | 분기 workflow |
|---|---|
| 새 feature (route + 컴포넌트 + 상태 + API) | `add-feature` → `code` |
| 새 디자인 시스템 컴포넌트 | `add-component` → `code` |
| 단순 UI/style 수정 | `code` |
| API 통합 추가 | `code` (api-client + tanstack-query) |
| bug fix | `code` (회귀 테스트 필수) |
| 결정 검증 | `poc` → ADR |
| 배포 | `cicd` → `release` |
| 긴급 fix | `hotfix` (예외 entry. 24h 사후 이슈) |

---

## 항상 적용

- 모든 작업은 이슈 티켓부터 (`workflows/task-flow`, `git/issue`)
- TypeScript strict (`config/tsconfig`)
- ESLint + Prettier (`config/eslint-prettier`)
- Conventional Commits + signed (`git/commit`, `git/signing`)
- 1 PR 1 논리 변경 (`git/pr`)
- 6 axis review (`git/review`, `workflows/code-review`)
- secret leak 차단 (`config/env`, `security/security`, `git/gitignore`)
- a11y (jsx-a11y lint + 수동 점검)
- cleancode·oop (`code/*`)

## 백엔드(`prompt/`)와 공유

`code/`, `git/`, `docs/`, `docker/`, `observability/`(frontend 보강), `security/`(frontend 재작성), workflows의 `task-flow`, `code-review`, `cicd`, `hotfix`, `release`, `poc`는 백엔드와 동일 정책. 분기 workflow(`add-feature`, `add-component`)와 frontend 특화 framework는 본 저장소만.

## 인덱스 갱신 책임

- 새 폴더·feature·convention·workflow 추가 시 `index.yml` 갱신 의무
- 새 동적 로드 패턴이면 본 파일 매트릭스 갱신

---

## 호출 흐름

```
이슈 → triage → plan → eval-plan → generate-plan → branch
  → generate → eval → squash merge → cicd(dev) → tag → release(prod)
```
