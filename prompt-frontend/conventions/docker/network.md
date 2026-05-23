## rule
- compose top-level `networks:` 에 명시 정의 (`backend`, `frontend` 등). default network 의존 X.
- 1 compose stack = 1~2 network. backend(internal-only) + edge(reverse proxy 노출) 분리 권장.
- 외부 노출은 reverse proxy(nginx/traefik) 1 service만. app/db는 internal-only.
- 포트 매핑(`ports:`)은 외부 노출이 의도된 서비스에만. internal 통신은 `expose:` 또는 service name 사용.
- service 간 통신은 service name DNS (`postgres:5432`). IP 하드코딩 X.
- prod 환경에서는 dev 전용 포트 제거 (debug, JMX, DB direct port).
- network 분리로 보안 경계: app → db 허용, frontend → db 차단.
- 동일 host의 다른 stack과 network 공유 시 `external: true`로 명시.
- network 이름은 prefix 명확화 (`indoor-vps_backend` 자동 생성 또는 명시).

## forbidden
- default network 의존 (격리 깨짐, 다른 stack과 충돌)
- DB/내부 서비스를 host 포트로 노출 (`ports: ["5432:5432"]` in prod)
- host network mode (`network_mode: host`) — 격리 깨짐
- 1 service에 여러 무관한 외부 포트 노출
- service IP 하드코딩 (DNS 사용)
- prod에서 dev 전용 포트(JMX, debug, DB 5432) 노출
- 외부 노출 service에 인증 없이 admin endpoint 활성
- network 이름 충돌 (다른 stack과 같은 이름이지만 다른 정의)
- 같은 컨테이너에서 frontend·backend 양쪽 network 동시 부착 (반드시 reverse proxy 분리)
