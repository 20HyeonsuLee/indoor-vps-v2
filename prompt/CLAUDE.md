# AI Agent 컨벤션·워크플로우 참조 가이드

이 프로젝트는 두 인덱스 루트를 둔다.

- **`prompt/index.yml`**: 프로젝트 구조 + 각 경로에 적용되는 `_conventions` / `_workflows` 매핑. 구조를 한 눈에 보고, 변경 대상 경로에서 어떤 룰이 적용되는지 즉시 찾을 수 있게 한다.
- **`prompt/CLAUDE.md`** (이 파일): 줄글로 설명해야 하는 것. 에이전트 유형별 참조 룰, 동적 로드 정책, 6하원칙 기반 가이드.

agent는 작업 시작 전 **루트 `CLAUDE.md` → `prompt/index.yml` → `prompt/CLAUDE.md`** 순으로 로드한다. 실제 변경 대상 경로가 정해지면 `index.yml`의 그 경로 `_conventions`·`_workflows`만 추가 로드한다(동적 로드).

---

## 에이전트 유형 (하네스 기준)

| 유형 | 단계 | 책임 |
|---|---|---|
| **plan** | 1 | 요구사항 → 설계 결정·ADR 초안. 결정 옵션 비교, 트레이드오프 정리 |
| **generate-plan** | 2 | 구현 분할 plan. 어떤 파일·layer·테스트 단위로 쪼개는지 |
| **generate** | 3 | 실제 코드 작성·수정 |
| **eval-plan** | 2.5 | plan 검토 (설계 일관성·ADR 부합·위반 위험) |
| **eval** | 4 | 구현 결과 검토 (코드 품질·convention·테스트) |

각 유형은 다른 자산을 만들고 다른 룰을 본다. 아래 6하원칙으로 정리한다.

---

## plan 에이전트

- **누가 (Who)**: 새 기능, ADR 결정, architecture 변경, 외부 라이브러리 도입 결정을 시작할 때 호출되는 agent
- **언제 (When)**: PRD 또는 사용자 요구 수신 시. 결정이 필요한 시점(decision 이슈 open)에도
- **무엇 (What) 참조**:
  - 항상: `docs/ard.md` (ADR 형식), `docs/prd.md` (PRD 형식), `docs/glossary.md` (도메인 용어), `architecture/*` (layer 경계)
  - 도메인 신설 검토: `workflows/add-context.md`
  - Python 통합 결정: `workflows/add-python-pipeline.md`, `python/protocol.md`
  - PoC 단계: `workflows/poc.md`
  - 기존 결정 확인: `docs/decisions/*` 인덱스
- **어디 (Where) 영향**: `docs/decisions/`, `docs/prd/`, `docs/tasks/<seq>/plan.md`
- **왜 (Why)**: 결정 일관성, ADR 추적, 범위 명확화. 잘못된 결정은 모든 후속 단계 회귀
- **어떻게 (How)**:
  1. glossary로 도메인 어휘 확정
  2. 옵션 ≥ 2 비교 (Alternatives 섹션)
  3. 트레이드오프 명시
  4. Decision + Consequences 명시
  5. ADR 또는 PRD 산출물 생성

---

## generate-plan 에이전트

- **누가**: ADR/PRD 확정 후 구현 분할 단계 agent
- **언제**: plan 완료 직후, generate 진입 직전
- **무엇 참조**:
  - 항상: 직전 plan 산출물, `architecture/*`, 영향 layer의 `architecture/<layer>/*`, `git/pr.md`, `git/branch.md`, `workflows/code.md`
  - DB 변경: `framework/flyway.md`, `workflows/migration.md`
  - 새 context: `workflows/add-context.md`
  - Python pipeline: `workflows/add-python-pipeline.md`
  - 배포 영향: `workflows/release.md`, `workflows/cicd.md`
- **어디 영향**: `docs/tasks/<seq>/plan.md` (task 분할 plan), 또는 PR description 초안
- **왜**: 1 PR 1 논리 변경 원칙 + Expand-Contract 보장. 큰 변경을 안전한 작은 PR로 분해
- **어떻게**:
  1. 변경 layer 식별 (architecture)
  2. PR 분할 결정 (migration 있으면 3-PR 패턴 검토)
  3. 각 PR의 입력·출력·테스트 명시
  4. PR 의존 순서·배포 순서 명시
  5. risk·rollback 시나리오

---

## generate 에이전트

- **누가**: 실제 코드 작성·수정 agent
- **언제**: generate-plan 확정 후
- **무엇 참조**:
  - 항상: `code/cleancode.md`, `code/oop.md`, `git/commit.md`, `git/branch.md`, `workflows/code.md`
  - 변경 layer에 따라 동적 로드 (아래 매트릭스)
  - 테스트 동반: `framework/junit.md`, `framework/karate.md`, `workflows/test.md`
  - DB 변경: `framework/flyway.md`, `workflows/migration.md`
  - Docker 변경: `docker/*`
  - Python 변경: `python/*`
  - 보안 영향: `security/security.md`, `config/env.md`, `git/gitignore.md`
- **어디 영향**: `src/`, `python/`, `docker/`, `src/main/resources/db/migration/`, `src/test/`, `.github/`
- **왜**: convention 일관성·회귀 차단·재현성
- **어떻게**:
  1. `index.yml`에서 변경 대상 경로의 `_conventions`·`_workflows` 읽기
  2. workflow 절차대로 진행 (outside-in: Karate → domain → UseCase → Repository → Controller)
  3. PR open 전 자체 6 axis review (`git/review.md`)

---

## eval-plan 에이전트

- **누가**: plan 검토 agent (read-only)
- **언제**: plan 작성 후 generate 진입 전
- **무엇 참조**:
  - `architecture/*`, `docs/ard.md`, 관련 ADR, `workflows/*`, `git/review.md`
- **어디 영향**: read-only. 코멘트·suggestion만
- **왜**: 잘못된 plan으로 generate가 회귀 만드는 비용을 사전 차단
- **어떻게** (6 axis):
  1. **도메인 일관성**: glossary·ADR 부합
  2. **layer 경계**: architecture 룰 위반 없음
  3. **변경 분할**: 1 PR 1 논리 변경, Expand-Contract
  4. **migration 안전**: forward-only, 백필 분리
  5. **테스트 전략**: outside-in BDD 동반
  6. **보안**: secret leak, 인증·인가 영향

---

## eval 에이전트

- **누가**: 구현 결과 PR 검토 agent (read-only)
- **언제**: PR open 후, merge 전
- **무엇 참조**:
  - 항상: `git/review.md`, `git/pr.md`, `git/commit.md`, `code/*`
  - 변경 layer의 `architecture/*`, `framework/*`
  - migration 있으면 `framework/flyway.md`, `workflows/migration.md`
  - Docker 변경 시 `docker/*`
  - secret 변경 시 `config/env.md`, `git/gitignore.md`, `git/signing.md`, `security/security.md`
  - 테스트 검토 시 `framework/junit.md`, `framework/karate.md`
- **어디 영향**: read-only. PR review 코멘트
- **왜**: 회귀 사전 차단·convention 강제·6 axis 일관 적용
- **어떻게**: `workflows/code-review.md` 6 axis 적용. 코멘트 prefix(`nit:`/`q:`/`suggest:`/`blocker:`).

---

## 동적 로드 트리거 (변경 파일 → 로드 convention)

`generate`·`eval`는 작업 대상 경로가 정해지면 아래 매트릭스대로 추가 로드한다.

| 변경 패턴 | 로드할 convention |
|---|---|
| `src/main/java/.../app/**` | `framework/springboot`, `code/cleancode`, `code/oop` |
| `src/main/java/.../config/**` | `framework/springboot`, `config/application` |
| `src/main/java/.../shared/**` | `code/cleancode`, `code/oop` |
| `src/main/java/.../contexts/<context>/application/**` | `architecture/application/service`, `architecture/application/port`, `framework/springboot`, `code/*` |
| `src/main/java/.../contexts/<context>/domain/**` | `architecture/domain/*`, `framework/jpa`, `code/*` |
| `src/main/java/.../contexts/<context>/domain/*/port/**` | `architecture/application/port` (port 정책은 application/port와 공유), `architecture/domain/*` |
| `src/main/java/.../contexts/<context>/infrastructure/web/**` | `architecture/ui/controller`, `architecture/ui/dto`, `framework/springboot`, `code/*` |
| `src/main/java/.../contexts/<context>/infrastructure/{rtabmap,localization,capture,bridge}/**` | `architecture/infrastructure/external` (또는 vision adapter면 `python/protocol`), `framework/springboot`, `code/*` |
| `src/main/java/.../contexts/<context>/infrastructure/storage/**` | `architecture/infrastructure/persistence`, `framework/jpa` (앱 DataSource면) |
| `src/main/resources/application*.yml` | `config/application`, `config/env` |
| `src/main/resources/db/migration/**` | `framework/flyway`, `workflows/migration` |
| `src/test/java/**` | `framework/junit`, `workflows/test`, `code/*` |
| `src/test/resources/karate/**` | `framework/karate`, `workflows/test` |
| `docker/**`, `Dockerfile*`, `docker-compose*.yml` | `docker/*`, `workflows/cicd` |
| `python/<pipeline>/**` | `python/uv`, `python/protocol`, `workflows/add-python-pipeline` |
| `docs/decisions/**` | `docs/ard` |
| `docs/prd/**` | `docs/prd` |
| `docs/glossary*.md` | `docs/glossary` |
| `.github/workflows/**` | `workflows/cicd`, `docker/image`, `git/release` |
| `.gitignore` | `git/gitignore` |
| `.gitattributes` | `git/gitattributes` |
| `.github/ISSUE_TEMPLATE/**`, `.github/CODEOWNERS`, `.github/pull_request_template.md` | `git/issue`, `git/codeowners`, `git/pr` |
| **모든 PR 공통** | `code/cleancode`, `code/oop`, `git/commit`, `git/pr`, `git/review` |

## 작업 종류 → 워크플로우 매트릭스

**모든 작업은 `workflows/task-flow` 가 entry point**. 이슈 티켓 발급으로 시작. 그 안에서 작업 종류별 추가 workflow를 분기 호출.

| 작업 | entry | 추가 workflow |
|---|---|---|
| 새 기능 구현 | `task-flow` | `code` |
| 코드 리뷰 | `task-flow` step 6 | `code-review` |
| 테스트 작성 | `task-flow` 내 | `test` |
| 배포 (정상) | `task-flow` merge 후 | `cicd` → `release` |
| 긴급 fix | `hotfix` (예외 entry) | `task-flow` 사후 이슈 |
| DB 변경 | `task-flow` | `code` + `migration` |
| 새 Bounded Context | `task-flow` | `add-context` → `code` |
| Python pipeline 추가 | `task-flow` | `add-python-pipeline` → `code` |
| 결정 검증·실험 | `task-flow` (decision 이슈) | `poc` → ADR |

---

## 항상 적용되는 룰 (PR마다)

- **모든 작업은 이슈 티켓 발급부터** (`workflows/task-flow`, `git/issue`)
- Conventional Commits (`git/commit.md`)
- 1 PR 1 논리 변경, 1 PR 1 migration 최대 (`git/pr.md`)
- 6 axis review (`git/review.md`, `workflows/code-review.md`)
- secret leak 차단 (`git/gitignore.md`, `security/security.md`, `config/env.md`)
- cleancode·oop (`code/*`)

## 변경 금지 (CLAUDE.md root `forbidden`)

루트 `CLAUDE.md`의 `forbidden` 섹션은 이 프로젝트의 마지막 가드레일. 모든 agent는 그 forbidden을 위반하지 않는다. 위반 발견 시 plan/generate는 차단, eval은 blocker로 표시.

---

## 인덱스 갱신 책임

- 새 폴더·context·workflow·convention 추가 시 **`prompt/index.yml` 갱신 의무**
- 그 매핑이 새로운 동적 로드 패턴이라면 **본 파일 (CLAUDE.md) 매트릭스도 갱신**
- 두 인덱스가 sync 안 되면 agent가 잘못된 convention 로드 → 회귀. PR review 시 검출.

## 에이전트 호출 흐름 (한 task 기준)

```
[user 요구]
  ↓
[issue 발급]   →  workflows/task-flow step 1. .github/ISSUE_TEMPLATE
  ↓
[triage]      →  task-flow step 2. label·assignee·우선순위
  ↓
plan          →  ADR/PRD 산출. docs/decisions, docs/prd, docs/tasks
  ↓
eval-plan     →  plan 6 axis 검토 (read-only)
  ↓
generate-plan →  PR 분할 plan. docs/tasks/<seq>/plan.md
  ↓
eval-plan     →  분할 plan 검토 (선택)
  ↓
[branch]      →  task-flow step 3. main에서 분기
  ↓
generate      →  실제 코드. workflows/code. PR 1+ open
  ↓
eval          →  PR 6 axis review. workflows/code-review
  ↓
[squash merge] →  task-flow step 7. 이슈 auto close
  ↓
deploy        →  workflows/cicd → dev 자동 배포
  ↓
release tag   →  workflows/release → prod 배포
```

모든 단계는 `workflows/task-flow` 안에서 일관 lifecycle. 단 `hotfix`는 예외 entry로 incident → 복구 → 24h 내 사후 이슈.

각 단계의 산출물·convention·workflow는 위 6하원칙 섹션 참조.
