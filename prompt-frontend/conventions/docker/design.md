## rule (Dockerfile 설계)
- **stage = 책임 단위**. 한 stage는 한 책임만. 표준 패턴:
  - `base` (공통 base + ARG·ENV)
  - `deps` (의존성 install — gradle deps, pip deps)
  - `build` (소스 컴파일·jar 패키징)
  - `test` (선택 — 단위 테스트 분리)
  - `runtime` (final — jar + entry point만)
- **`FROM base AS <stage>`로 stage 공유**. 같은 base를 여러 stage가 참조해 일관성 확보.
- ARG는 stage 경계를 넘지 않는다. 각 stage에서 필요한 ARG는 다시 선언. `ARG BASE_IMAGE`를 stage마다 재선언하는 패턴이 표준.
- **multi-target 사용**: 한 Dockerfile에서 dev/prod/debug 같은 final stage 여러 개 정의. `docker build --target=runtime` 으로 선택. 환경별 Dockerfile 복제 회피.
- **base ARG로 base image 한 줄 변경 가능하게**:
  ```dockerfile
  ARG BASE_IMAGE=ghcr.io/owner/repo/base@sha256:...
  FROM ${BASE_IMAGE} AS runtime
  ```
- **deps stage와 build stage 분리**. deps는 manifest만 COPY → install (cache 친화). build는 그 위에 src COPY → 컴파일. 코드만 변경 시 deps stage cache hit.
- **external script 추출**: 5줄 초과 RUN은 `scripts/<name>.sh`로 빼고 `COPY scripts/ /scripts/` → `RUN /scripts/<name>.sh`. 가독성 + lint(shellcheck) 가능.
- **OCI labels는 final stage 한 곳에 그룹**. `org.opencontainers.image.source/revision/created/version`.
- **HEREDOC 활용** (BuildKit 1.4+, `# syntax=docker/dockerfile:1.4` 필수). 여러 줄 shell·텍스트 인라인 작성을 가독성 있게:
  ```dockerfile
  RUN <<EOF
  set -eux
  useradd -m -u 1000 appuser
  install -d -o appuser -g appuser /app
  EOF
  ```
- **final stage는 thin**. COPY + USER + EXPOSE + HEALTHCHECK + ENTRYPOINT만. 빌드/설치 명령 X.
- **stage 명명은 의미**: `base`, `gradle-deps`, `pip-deps`, `build`, `test`, `runtime`. `stage1`/`stage2` 금지.

## rule (docker-compose 설계)
- **base + override + profile 3축**:
  - base = 모든 환경 공통
  - `.override.yml` 또는 `.<env>.yml` = 환경 차분
  - profile (`profiles: [dev, debug, test]`) = 같은 환경 안 선택적 서비스
- **anchor로 service template 표준화**:
  ```yaml
  x-app-defaults: &app-defaults
    restart: unless-stopped
    healthcheck: { ... }
    logging: { driver: json-file, options: { max-size: 10m } }
  services:
    app:
      <<: *app-defaults
      ...
  ```
- **신뢰성 settings(healthcheck/depends_on/restart/logging)을 anchor로 표준화**. service마다 복붙 X.
- **env_file 분리**: 평문 environment 5+ 항목이면 `env_file: ./env/app.env`로 추출. base/local/prod env_file 분리.
- **서비스 명명 = 역할** (`app`, `db`, `cache`, `proxy`, `worker`). `service1` 금지.
- **build context는 최소**. `context: ./<subdir>`로 build 대상 한정. 루트 컨텍스트 + ignore 의존은 마지막 수단.
- **volumes / networks는 top-level에 명시**. service 안 inline 정의 지양.
- **secrets는 compose `secrets:` 또는 env_file 외부 참조**. base yml에 평문 X.
- **`--profile`로 선택적 활성화**: 디버그 컨테이너, 시드 job, lint runner 등 평소 안 띄우는 service.

## forbidden
- 환경별 Dockerfile 복제 (`Dockerfile.local`/`Dockerfile.prod` 거의 동일) — multi-target + ARG로 통합
- 익명 stage 또는 의미 없는 stage 명명
- base image 하드코딩 (ARG로 추출해 한 줄 변경)
- final stage에 빌드/install 명령 (build stage로)
- 1 Dockerfile에 무관한 책임 (build server + app runtime 한 이미지)
- ARG를 모든 stage 안에 산발 (필요 stage에 최소 재선언)
- compose에서 신뢰성 setting을 service마다 복붙 (anchor로)
- service 간 공통 설정 inline 중복 (anchor or env_file)
- 1 compose 파일에 모든 환경 분기 (override/profile로)
- 환경 분리를 compose 안 if/그 외 분기로 시도 (compose는 분기 없음 — 파일 분리)
- inline shell script가 stage 본문 절반 차지 (외부 .sh 추출)
