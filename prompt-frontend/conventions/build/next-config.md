## rule
- `next.config.ts` (TypeScript) 사용. `.mjs`도 OK.
- 필수 설정:
  - `output: 'standalone'` (Docker 이미지 최소화)
  - `reactStrictMode: true`
  - `images.remotePatterns`: 허용 외부 도메인 명시
  - `experimental.typedRoutes: true` (Next 15 — 타입 안전 링크)
- `env` 필드로 클라이언트 노출 env 명시 (단 `NEXT_PUBLIC_` prefix 권장).
- security header는 middleware 또는 `headers()`:
  - HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP
- bundle analyzer는 dev/CI 용 (`@next/bundle-analyzer`).
- `webpack` custom은 최소. plugin 추가는 ADR.
- redirect/rewrite는 `redirects()` / `rewrites()` 함수로 명시. middleware는 동적 분기 한정.
- i18n 필요 시 App Router 기반 패턴 (next-intl 또는 자체 segment).
- experimental flag 사용 시 PR description에 risk 명시. 다음 minor에서 변경 가능.

## forbidden
- `next.config` 안 비즈니스 로직
- secret을 `env` 필드에 (런타임 주입 사용)
- `output: 'standalone'` 누락 후 Docker (이미지 비대)
- security header 누락 (CSP·HSTS·X-Frame)
- 모든 외부 이미지 도메인을 wildcard로 허용
- middleware에 무거운 로직 (edge runtime 제약)
- experimental flag를 ADR·문서 없이 prod
- webpack custom으로 next 표준 패턴 우회
- `redirects()`에 외부 도메인 무검증 (open redirect)
- TypeScript ignoreBuildErrors (`typescript.ignoreBuildErrors: true`)
- ESLint ignoreDuringBuilds (`eslint.ignoreDuringBuilds: true`)
