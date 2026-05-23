## rule (logging — framework/logback과 짝)
- 로그는 SLF4J + logback. JSON 구조화. 자세한 룰은 `framework/logback.md`.
- MDC 필수 키: `traceId`, `requestId`, `userId`(인증 시), `useCase`(UseCase 진입 시).
- 모든 UseCase 진입·완료는 INFO 로그 1줄 (정상 흐름 trail).
- 도메인 예외(WARN)·infra 예외(ERROR)는 GlobalExceptionHandler에서 일관 매핑.

## rule (metrics)
- 메트릭은 **Micrometer** (Spring Boot Actuator 내장). Prometheus 또는 OTel exporter.
- 표준 메트릭 노출: HTTP request 수/지연, JVM heap, GC, datasource pool, Tomcat thread.
- 도메인 메트릭은 `app.<context>.<event>` prefix (`app.scan.uploaded_total`, `app.localize.latency_seconds`).
- 메트릭 타입:
  - Counter: 단조 증가 (요청 수)
  - Gauge: 현재 값 (큐 사이즈)
  - Timer/Histogram: 지연·분포
- 태그(label) 카디널리티 ≤ 50. high-cardinality(userId, traceId)는 메트릭 태그 X (로그/trace로).
- exposure endpoint: `/actuator/prometheus`. 인증 또는 internal-only.

## rule (tracing)
- 분산 추적은 **Micrometer Tracing** + Brave 또는 OpenTelemetry.
- traceId는 inbound header(`traceparent` W3C)로 받음. 없으면 생성. outbound 호출에 propagate.
- ProcessBuilder Python 호출도 traceId env로 전달 (`TRACE_ID=...`). Python 출력 로그에 같은 traceId 박음.
- span은 UseCase 단위 + 외부 호출 단위. domain 내부 메서드는 span X (noise).

## rule (health)
- `/actuator/health`: liveness + readiness. (docker/healthcheck.md 짝)
- 의존성 health: DB, 외부 API. 실패 시 readiness fail.
- liveness ≠ readiness. liveness는 process 살아있음만, readiness는 트래픽 받을 준비.

## rule (alert)
- alert은 SLO 기반. 5xx 비율, P99 지연, queue lag.
- alert 임계는 runbook과 짝. 임계 발화 시 무엇을 보고 무엇을 조치할지.
- 알람 noise 최소. flap 방지 (5분 sustained).

## forbidden
- `System.out.println`·`printStackTrace` (logback 사용)
- traceId 없는 로그 (MDC 누락)
- 메트릭 태그에 high-cardinality (userId·traceId·UUID 등)
- 메트릭 endpoint를 인증 없이 외부 노출
- health endpoint에 비밀값/내부 상태 노출
- alert 임계 없이 메트릭만 수집 (수집만 X)
- alert이 runbook 링크 없이 발화 (대응 정보 누락)
- ProcessBuilder Python 호출에 traceId 전파 누락 (분산 trace 끊김)
- liveness와 readiness 같은 endpoint 사용
- 도메인 객체를 메트릭 태그로 통째 사용 (필드 폭증)
