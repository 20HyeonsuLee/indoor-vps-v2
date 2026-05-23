## rule (auth)
- 인증 token은 **httpOnly cookie** 우선. JWT를 localStorage/sessionStorage에 저장 금지(XSS).
- 인증 상태는 server에서 확인(middleware 또는 server component). client 단독 신뢰 X.
- 인가 분기는 server 우선. client는 보조 UX (보호된 페이지 표시).
- session 만료 시 자동 logout + 로그인 페이지 redirect.
- OAuth/OIDC 사용 시 PKCE flow. implicit grant 금지.

## rule (XSS)
- React는 default escape. 단 `dangerouslySetInnerHTML`은 검토 필수.
- 사용자 입력을 그대로 innerHTML/SVG로 삽입 금지. 필요 시 sanitize lib (DOMPurify).
- markdown 렌더 시 sanitize 옵션 활성.
- CSP 헤더 (Content-Security-Policy) — `next.config`/middleware에서 설정.
  - `script-src 'self'` (necessary nonce/hash만 추가)
  - `style-src 'self' 'unsafe-inline'` (Tailwind inline 허용 시)
  - `img-src 'self' data: <whitelist>`
  - inline event handler 금지

## rule (CSRF)
- 인증 cookie 사용 시 SameSite=Lax/Strict + Secure + httpOnly.
- state-changing 요청은 same-origin (BFF 패턴) 또는 CSRF token.
- form submit은 server action 권장 (Next.js).

## rule (secrets)
- `NEXT_PUBLIC_*` 환경변수는 **public** 취급. secret 절대 X.
- API key·DB password는 server-only env. server action 또는 API route에서만 사용.
- third-party SDK key가 client에 필요하면 권한 최소화 + 도메인 whitelist (Stripe publishable, GA measurement 등).
- secret rotation 90일.
- secret scan: pre-commit (gitleaks) + CI.

## rule (input validation)
- 모든 user input은 Zod schema로 검증.
- form: React Hook Form + Zod.
- API request: Zod parse.
- URL params/searchParams도 Zod 검증.
- file upload: 크기 한도·content-type whitelist·확장자 검증·재네이밍.
- redirect URL: 외부 도메인 차단 (open redirect).

## rule (transport)
- HTTPS 강제. HTTP → HTTPS redirect.
- HSTS 헤더.
- mixed content 금지.

## rule (dependency)
- Dependabot 또는 Renovate 활성.
- 보안 알림 우선순위: critical 24h, high 1주, medium 1달.
- 라이선스 검증: GPL/AGPL 의존성 ADR.

## rule (UI security)
- credential·token을 URL query string 노출 X (URL은 로그·history에 남음).
- error 응답에 stacktrace·내부 경로 X.
- 외부 link는 `rel="noopener noreferrer"`.
- iframe sandbox 명시.

## forbidden
- token을 localStorage/sessionStorage 평문 저장
- secret을 `NEXT_PUBLIC_*`로 노출
- 인증을 client 단독 신뢰 (server 검증 필수)
- 사용자 input을 `dangerouslySetInnerHTML`에 직접
- CSP 헤더 누락
- inline event handler (`onclick="..."`)
- `target="_blank"` + `rel` 누락
- HTTP로 cookie 설정 (Secure flag 누락)
- SameSite 미설정 cookie
- error response에 stacktrace 노출 (prod)
- Open redirect 검증 누락
- 의존성 취약 알림 무시
- secret을 build image에 bake
- form CSRF token 없이 cross-origin POST
- 사용자 file 원본 이름 그대로 저장 (path traversal)
