## rule
- Next.js 15+ **App Router**. Pages Router 신규 도입 X.
- React 19+. server component(RSC) 기본, client component는 leaf.
- 데이터 fetch:
  - server: page/layout에서 `await fetch(...)`. Next 확장 (`{ next: { revalidate, tags } }`)으로 cache 제어
  - client: TanStack Query
- **server action** 사용 권장 (`"use server"`). form submit·mutation은 server action으로 type-safe.
- metadata는 `generateMetadata` (SEO).
- caching:
  - Next 15부터 fetch default uncached. 명시적 `cache: 'force-cache'` 또는 `next: { revalidate }`
  - mutation 후 `revalidatePath` / `revalidateTag`
- middleware는 `middleware.ts` (root). 인증 redirect·rewrite만. 무거운 로직 X.
- 환경변수: `NEXT_PUBLIC_*`만 client 노출.
- output mode는 **`standalone`** (Docker 최소화).
- image는 `<Image>` (next/image). 외부 도메인은 `images.remotePatterns` 등록.
- font는 `next/font` (FOIT 방지).
- 빌드는 `next build`. dev는 `next dev`.

## forbidden
- Pages Router 신규 추가
- `"use client"`를 page level에 (data fetch 못함)
- client에서 secret env 접근 (`NEXT_PUBLIC_` 외)
- `<img>` 직접 (LCP 손해)
- 외부 도메인 미등록 image
- middleware에 DB 접근 / 무거운 로직 (edge 제약)
- API route를 BFF 외 용도로 남용 (server action 우선)
- caching 의미 모른 채 `cache: 'no-store'` 무분별
- standalone 옵션 없이 Docker 빌드
- critical render path에 dynamic import (FCP 지연)
