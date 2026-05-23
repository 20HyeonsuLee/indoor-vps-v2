## rule (logging)
- client 로그는 console 사용 최소. structured logger (pino-browser) 또는 외부 서비스로.
- 사용자 추적 가능 정보(PII, token)는 절대 로그·이벤트에 노출 X.
- server-side(API route·server action·middleware) 로그는 JSON 구조화.
- traceId·requestId 헤더 propagation (`x-request-id`, `traceparent`).

## rule (error tracking)
- **Sentry** 권장 (또는 Datadog RUM). client + server 둘 다 통합.
- 에러 매핑:
  - 도메인 검증 실패(4xx) → warn
  - 네트워크/서버 실패(5xx) → error + 사용자 친화 메시지
  - uncaught → error boundary가 catch + Sentry 전송
- Sentry init은 `instrumentation.ts` (Next 15+).
- sensitive 데이터 redaction 활성 (`beforeSend` hook).
- release tag로 source map 업로드 (debug 용이).

## rule (analytics / RUM)
- **PostHog / GA4 / Vercel Analytics** 중 택1.
- web vitals (LCP·CLS·INP·FCP·TTFB) 측정 + dashboard.
- 사용자 식별은 anonymous id 우선. 로그인 사용자만 distinct id.
- consent 기반 (GDPR/CCPA): opt-in 없으면 tracking X.
- event 명명 규약: `<domain>.<action>` (`auth.login_succeeded`, `checkout.completed`).

## rule (performance)
- LCP < 2.5s, INP < 200ms, CLS < 0.1 목표.
- Next 빌드 시 bundle size 점검 (`@next/bundle-analyzer`).
- 큰 image는 `<Image>` + format(`webp`/`avif`).
- font는 `next/font` (no FOIT).
- code splitting은 default (Next route 자동). 큰 client component는 `next/dynamic`.

## rule (health)
- API health는 백엔드 책임. frontend는 BFF route `/api/health` 또는 host platform readiness check.

## rule (alert)
- SLO 기반 alert (error rate, P99 latency, web vitals).
- 알람은 runbook 링크 포함.
- noise 최소 (5분 sustained, dedup).

## forbidden
- `console.log` prod commit
- PII·token·credential을 로그·이벤트에 노출
- Sentry redaction 없이 raw error 전송 (PII leak)
- analytics를 consent 없이 활성
- web vitals 모니터링 누락
- bundle size 측정 없이 prod 배포
- traceId 누락 (분산 trace 끊김)
- error boundary 없는 page
- analytics event 명명 무질서 (`<domain>.<action>` 통일)
- source map 미업로드 (Sentry stacktrace 의미 없음)
- alert 임계 없이 메트릭만 수집
