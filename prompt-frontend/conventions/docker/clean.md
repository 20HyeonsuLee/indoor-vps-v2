## rule (Dockerfile 가독성)
- 1 의도 = 1 stage 또는 1 RUN. 여러 의도를 한 RUN에 묶지 않는다. (`apt install` + `wget` + `make` 혼합 X)
- RUN chaining(`&&`)은 같은 의도 안에서만 사용. layer 줄이려고 무관한 명령 묶기 X.
- 긴 RUN은 multi-line backslash. 한 줄 ≤ 100자.
- ENV/ARG는 파일 상단에 그룹화. RUN 사이에 흩뿌리지 않는다.
- COPY는 의도 단위 분리. 의존성 manifest(`build.gradle.kts`, `pyproject.toml`)를 먼저, 코드는 나중에 (cache 친화).
- 주석은 stage 경계 또는 비자명 작업에만. WHAT 설명 주석 금지.
- shell 명령이 5줄 초과면 별도 `scripts/<name>.sh` 추출 후 `COPY + RUN` 호출.
- `apt-get install`은 `--no-install-recommends` + 끝에 `rm -rf /var/lib/apt/lists/*`.
- 매직값은 `ARG`로 추출 (`ARG JRE_VERSION=21`).
- stage 이름은 의미 단위 (`AS builder`, `AS python-deps`, `AS runtime`). 익명 stage 금지.
- final stage는 가장 짧게. 복사·CMD·HEALTHCHECK 외 명령 최소화.

## rule (docker-compose 가독성)
- 공통 설정은 YAML anchor + alias로 추출 (`x-common: &common ...`, `<<: *common`).
- env_file 사용해 environment 평문 나열 줄이기. 같은 service에서 env 5개 초과면 env_file로.
- 긴 command는 `>-` 또는 `|`로 multi-line.
- ports는 의미 단위 정렬 (app → db → cache 순). 같은 service의 ports/volumes/env는 일관된 순서.
- override 파일은 원본의 key 구조 그대로 따라간다 (diff 명확). 새 key 도입 시 base에 default 둠.
- profile 사용 (`profiles: [dev, debug]`)로 환경별 서비스 활성/비활성 분리.
- health/depends_on/restart 같은 신뢰성 설정은 service 정의 끝에 모아 둔다.
- 주석은 service 경계 또는 비자명 의도에만.
- top-level `volumes:`/`networks:`는 명시 정의 + 의미 단위 정렬.
- 1 compose 파일 ≤ 200 줄 권장. 초과 시 service 분리 또는 anchor 추출.

## forbidden
- 한 RUN에 무관한 명령 묶기 (의도 추적 어려움)
- ENV/ARG 산발 배치
- 익명 stage (`FROM ... AS` 누락)
- `apt-get install` 후 cache cleanup 누락
- shell heredoc 또는 inline 5줄 초과 (별도 .sh로)
- WHAT 설명 주석 (`# install java`)
- compose에서 같은 환경값 여러 service에 평문 복붙 (anchor/env_file로)
- override가 base의 key 구조 깨뜨림 (diff 추적 어려움)
- ports/volumes 정렬 무질서 (PR diff 노이즈)
- compose 파일 줄 수 > 200 (분리)
- 한 service 정의에 무관한 책임 묶기 (예: app + nginx + cron 한 컨테이너)
- yaml `version:` 키 사용 (compose v2 이후 deprecated)
