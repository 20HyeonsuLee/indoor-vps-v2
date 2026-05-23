## rule
- 무거운 deps(CUDA toolkit, RTAB-Map, OpenCV, PyTorch, LightGlue 같은 CV/SLAM 라이브러리)는 **custom base image**로 분리한다.
- base image는 별도 Dockerfile (`docker/Dockerfile.base`)에서 빌드해 registry(`ghcr.io/<owner>/<repo>/base`)에 push.
- 메인 Dockerfile은 `FROM ghcr.io/<owner>/<repo>/base@sha256:<digest>` 로 immutable digest 참조.
- base image 빌드는 별도 GitHub Actions workflow. trigger는 `docker/Dockerfile.base`, `python/pyproject.toml`, `requirements.txt` 등 deps manifest 변경 시만.
- 메인 빌드 workflow는 base를 pull만 한다 (재빌드 X). 코드 push마다 30초~수 분.
- 이미 잘 관리되는 외부 base image가 있으면 그것 사용 (예: `introlab3it/rtabmap:noble`). 자체 base는 그 위에 우리 추가 deps만 얹는 thin layer.
- base image 태그 변경(또는 digest 변경) 시 메인 Dockerfile의 `FROM` 라인 갱신 + ADR 또는 PR description에 명시.
- base image OCI label 명시: `org.opencontainers.image.source`, `revision`, `created`.
- base image 크기 < 8GB 권장 (registry 전송 비용). 초과 시 stage 분리 또는 deps 슬림화.
- 기존 외부 base에 없는 무거운 deps(PyTorch, LightGlue 등)는 자체 base에서 1회 install 후 layer로 박음.

## forbidden
- 무거운 deps를 매 빌드마다 메인 Dockerfile에서 install (빌드 시간 폭증)
- base image를 mutable tag(`:latest`, `:dev`)로 참조 (재현성 깨짐)
- base Dockerfile과 메인 Dockerfile 결합 (변경 빈도 다름 — 분리 필수)
- base image 변경을 PR description/ADR 명시 없이 푸시
- base image 안에 코드/secret bake
- 외부 base image를 fork·재배포 (라이선스/보안 risk)
- base image 빌드 workflow가 code push마다 trigger (의존성 변경 trigger만)
- base image 크기 > 10GB (registry pull 시간 폭증)
- 같은 deps를 여러 base image에 중복 install
