## rule
- 패키지 매니저는 **pnpm**. `package-lock.json`/`yarn.lock` 신규 도입 X. `pnpm-lock.yaml` 단일 SSOT.
- `package.json`의 `packageManager` 필드에 버전 pin (`"packageManager": "pnpm@9.x.x"`).
- 의존성 install: `pnpm install --frozen-lockfile` (CI 필수).
- 의존성 추가: `pnpm add <pkg>` / dev: `pnpm add -D <pkg>` / peer: `pnpm add --save-peer <pkg>`.
- exact version 고정 (`.npmrc`에 `save-exact=true`). caret/tilde 의존 X.
- workspace 도입 시 `pnpm-workspace.yaml` 명시. monorepo는 별도 ADR.
- scripts는 명시적 단순:
  - `dev`, `build`, `start`, `lint`, `format`, `test`, `test:e2e`, `typecheck`
- `pnpm dlx`로 일회성 도구 실행 (글로벌 install X).
- 사용 안 하는 의존성 점검: `pnpm dlx depcheck`.
- 보안 audit: `pnpm audit` + Dependabot/Renovate.
- 의존성 추가 시 PR description에 이유·라이선스·대안 명시 (build/dependencies와 짝).
- Node 버전은 `.nvmrc` 또는 `package.json` engines 명시 (LTS 권장).

## forbidden
- npm·yarn 혼용 (`package-lock.json`/`yarn.lock` 생성)
- caret/tilde version (`^1.2.3`/`~1.2.3` — exact 사용. ADR 거친 경우 제외)
- lock 파일 commit 누락
- 글로벌 install 의존 (`npm i -g`)
- `--no-frozen-lockfile`로 CI 우회
- 의존성 추가를 PR description 없이
- snapshot/beta 의존 (prod)
- 의존성 audit 무시
- packageManager 필드 누락 (corepack 활성 시 버전 drift)
- workspace 변경을 ADR 없이
