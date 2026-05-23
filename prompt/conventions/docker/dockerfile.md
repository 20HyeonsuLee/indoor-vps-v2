## rule
- Dockerfile은 `docker/Dockerfile` 1개를 기본으로 한다. 보조 이미지가 있을 때만 `Dockerfile.<purpose>`.
- multi-stage 빌드 필수. Python deps와 JVM 빌드를 분리한다.
- 단일 런타임 이미지에 Python + JRE + jar 동거. 별도 서비스 분리 X.
- stage 수 ≤ 5. 초과 시 공통 base 이미지 추출.
- base image는 tag pin (`eclipse-temurin:21-jre-jammy`, `python:3.12-slim`). `latest` 금지.
- 빌드 캐시 친화: 의존성 manifest(`build.gradle.kts`, `pyproject.toml`, `requirements.txt`)는 code COPY보다 위. 코드 변경에 deps install layer가 무효화되지 않게 한다.
- 패키지 다운로드는 **BuildKit cache mount** 필수 (`RUN --mount=type=cache,target=/root/.cache/pip ...`, `target=/root/.gradle`, `target=/var/cache/apt`). cache mount 사용 시 `--no-cache-dir` 제거 (layer 아닌 cache dir에 보존).
- 무거운 deps(CUDA/CV/SLAM 라이브러리)는 **custom base image**로 분리. 메인 Dockerfile은 `FROM <base>@sha256:<digest>` + 코드 layer만. 자세한 정책은 base.md 참조.
- 런타임 stage는 최소화 — JDK X, JRE만. build tool 제거.
- non-root user로 실행. `USER appuser` 명시.
- `HEALTHCHECK` 명시 (Spring Actuator `/actuator/health`).
- `.dockerignore`로 `build/`, `.gradle/`, `.git/`, `*.md` 제외.

## forbidden
- single-stage 빌드 (Python deps와 JVM 빌드 분리 필수)
- `latest` 태그 base image
- 패키지 install에 BuildKit cache mount 누락 (`pip`, `gradle`, `apt` 매 빌드 재다운로드)
- `--no-cache-dir` + cache mount 동시 사용 (cache mount 무력화)
- 매 빌드마다 무거운 deps(PyTorch, RTAB-Map, CUDA 등) 재설치 (base image로 분리)
- root user로 런타임 실행
- 이미지 안에 secret bake (`COPY .env`, build-arg로 비밀값 X)
- 빌드 산출물 외 캐시·임시 파일을 final layer에 남김
- 환경별 값 직접 명시 (compose override 사용)
- stage 수 > 5 (분리)
- final image에 JDK·Maven/Gradle wrapper 잔존
- 무분별한 `apt-get install` (필요 패키지만, `--no-install-recommends`)
- Python venv 없이 system pip 설치 (depedency 충돌)
