## rule
- compose는 base + override 2-layer. base = `docker-compose.yml`, prod override = `docker-compose.prod.yml`.
- 환경 파일에는 **차분만**. base와 같은 값 적기 금지 (config 중복 금지 — CLAUDE.md forbidden).
- 로컬 기동은 `docker compose up -d` (base만으로 동작 가능해야 함).
- prod 기동은 `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.
- 서비스 수 ≤ 8. 초과 시 도메인 분리 또는 compose profile 도입.
- 서비스 명명은 kebab-case 명사 (`app`, `postgres`, `python-vision`).
- 의존성은 `depends_on` + healthcheck condition 명시. 단순 `depends_on` 만으로 ready 보장 X.
- 볼륨은 명시 정의 (top-level `volumes:`). bind mount는 로컬 전용.
- 네트워크 분리: 외부 노출은 reverse proxy 한 서비스만.
- secrets는 env_file 또는 compose `secrets:`. 평문 yml 금지.
- 포트 매핑은 base에 개발용 포트, prod override에서 제거 또는 internal-only.
- 이미지 빌드는 base에서 build context 정의, prod override는 registry image 사용.

## forbidden
- override에 base 동일 값 적기
- 단일 compose 파일에 환경별 분기 (override 사용)
- 서비스 명 `latest` 같은 모호한 명명
- 평문 비밀값을 yml에 적기
- `depends_on` 만으로 ready 보장 의존 (healthcheck condition 사용)
- 서비스 수 > 8 (분리)
- 무한 restart 정책 없이 외부 의존 서비스 노출
- host 네트워크 모드 (격리 깨짐 — 정말 필요한 경우만)
- 빌드 캐시 무효화하는 `pull_policy: always` 남발
- prod에서 개발용 mount/포트 그대로
