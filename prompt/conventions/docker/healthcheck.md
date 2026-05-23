## rule
- Spring 앱 healthcheck endpoint: `/actuator/health` (Spring Boot Actuator). custom indicator 추가 가능.
- Dockerfile `HEALTHCHECK` 정의 표준:
  - `interval`: 30s
  - `timeout`: 5s
  - `retries`: 3
  - `start-period`: 60s (JVM warm-up + Flyway migration 대기)
- compose에서는 `healthcheck:` 블록으로 override. interval/timeout/retries/start_period 같은 키.
- 의존 서비스는 `depends_on: { service: condition: service_healthy }`로 연결. 단순 `depends_on`은 ready 보장 X.
- DB(`postgres`) healthcheck: `pg_isready -U <user> -d <db>`.
- 외부 산출물 의존(rtabmap SQLite 등)은 별도 readiness indicator로 분리.
- healthcheck endpoint는 인증 제외. 외부 노출 X (internal port만).
- liveness vs readiness 구분 필요 시 `/actuator/health/liveness`와 `/health/readiness` 분리.
- healthcheck 실패 시 컨테이너 restart 정책: `restart: unless-stopped` (prod).

## forbidden
- HEALTHCHECK 누락 (compose가 ready 판단 못함)
- `start-period` 너무 짧음 (JVM cold start 중 unhealthy 오판 → restart loop)
- healthcheck에 무거운 쿼리/외부 호출 (timeout 빈발)
- healthcheck endpoint 인증 강제 (의존 서비스가 호출 못함)
- `depends_on` 만으로 ready 가정
- liveness/readiness 같은 endpoint 사용 (의미 다름)
- healthcheck 실패를 무시하는 restart 정책 (`no`)
- healthcheck endpoint를 외부 포트로 노출
- prod에서 healthcheck interval > 60s (감지 지연)
