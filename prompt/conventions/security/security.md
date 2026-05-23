## rule (auth)
- 인증은 Spring Security 기반. JWT 또는 session 방식은 ADR로 결정.
- 인증 정보는 SecurityContext에서. Controller·UseCase 메서드 인자로 전달은 명시적 (`@AuthenticationPrincipal`).
- 인가는 layer 분리: HTTP/엔드포인트 인가는 `SecurityFilterChain`·`@PreAuthorize` (ui/Controller), 도메인 권한은 Aggregate Root 메서드 안.
- 모든 보호 endpoint는 default deny. `permitAll`은 명시 화이트리스트.
- credential·token은 절대 로그·예외 응답·에러 메시지에 노출 X.

## rule (secrets)
- secret은 환경변수 + GitHub Secrets (config/env.md 짝). 코드·yml 평문 금지.
- secret rotation 정책: 90일 권장. 사용 stop 시 즉시 revoke.
- third-party API key 추가 시 ADR 또는 PR description에 용도·범위·revoke 절차 명시.
- 빌드 시점 secret bake 금지 (Docker image에 secret 들어가면 image 보유자 = 노출).
- secret scan: pre-commit hook (gitleaks 또는 git-secrets) + CI 스캔.

## rule (input validation)
- HTTP 입력 검증은 Request DTO + Bean Validation (architecture/ui/dto.md).
- 도메인 invariant 검증은 VO 생성자 + Aggregate Root 메서드.
- file upload: 크기 한도·content-type whitelist·확장자 검증·재네이밍(uuid). 사용자 입력 파일명 그대로 저장 X.
- SQL injection: JPQL/PreparedStatement만. native query 시 `:param` binding 필수.
- path traversal: file path 입력 시 `Path.resolve` 후 root 안 포함 검증.
- redirect URL 검증: `open redirect` 방지. 외부 도메인 제한.

## rule (output / response)
- 에러 응답에 stacktrace·내부 경로·SQL 노출 X. 사용자 친화 메시지 + traceId만.
- API 응답에 도메인 객체 통째 X. Result/Response DTO로 변환 (필요 필드만).
- CORS: 명시 origin allowlist. `*` 사용 시 ADR.
- security header: HSTS, X-Frame-Options, X-Content-Type-Options, CSP. Spring Security default 활성.

## rule (dependency security)
- 의존성 보안 스캔: GitHub Dependabot 또는 `gradle dependencyCheck` 정기.
- 취약 의존성 발견 시 우선순위: critical 24h, high 1주, medium 1달.
- 라이선스 검증: GPL/AGPL 의존성은 ADR 거쳐 결정 (build/dependencies.md 짝).

## rule (transport)
- HTTPS 강제 (prod). HTTP listener는 redirect만.
- 내부 service 간 통신도 가능하면 TLS (compose network는 internal-only로 격리)

## forbidden
- secret을 git/yml/Dockerfile/log/exception 어디든 평문 노출
- 인증 우회 endpoint를 `permitAll`로 무분별 허용
- 도메인 객체를 인증·인가 결정에 안 쓰고 외부 input을 신뢰 (`@AuthenticationPrincipal` 누락 후 request body의 userId 사용)
- file upload에 확장자·content-type 검증 누락
- native SQL에 user input 직접 concat (SQL injection)
- path traversal 검증 누락
- error response에 stacktrace 노출 (prod)
- CORS `*` 무검토
- HTTP redirect chain 안에 외부 도메인 (open redirect)
- 의존성 취약 알림 ignore 누적
- image에 secret bake
- credential을 query string에 (URL은 로그에 박힘)
- session/cookie를 secure/httpOnly 없이 (XSS·CSRF risk)
