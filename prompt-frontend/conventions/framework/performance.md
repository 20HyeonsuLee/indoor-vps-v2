## 목표 (Core Web Vitals)
| 지표 | good | needs improvement | poor |
|---|---|---|---|
| **LCP** (Largest Contentful Paint) | < 2.5s | 2.5–4.0s | > 4.0s |
| **INP** (Interaction to Next Paint) | < 200ms | 200–500ms | > 500ms |
| **CLS** (Cumulative Layout Shift) | < 0.1 | 0.1–0.25 | > 0.25 |
| FCP | < 1.8s | 1.8–3.0s | > 3.0s |
| TTFB | < 0.8s | 0.8–1.8s | > 1.8s |

prod p75 기준으로 측정.

## rule (LCP 개선)
- LCP element는 보통 hero image·heading. priority hint:
  - `<Image priority>` (above-the-fold)
  - `<link rel="preload" as="image">` (custom asset)
- font는 `next/font` (FOIT 방지 + self-host).
- 외부 origin은 `<link rel="preconnect">`.
- server response time(TTFB) 단축: static·ISR·캐시.
- render-blocking JS·CSS 최소.

## rule (INP 개선)
- 무거운 JS는 dynamic import (`next/dynamic`).
- main thread blocking 함수는 `requestIdleCallback` 또는 web worker.
- React: `useTransition` / `useDeferredValue`로 non-urgent update 미루기.
- 이벤트 핸들러 debounce/throttle.
- 큰 list는 virtualization (`@tanstack/react-virtual`).

## rule (CLS 개선)
- image/video는 `width`/`height` 명시 (또는 `<Image>` fill + aspect-ratio).
- skeleton/placeholder로 동적 콘텐츠 공간 예약.
- 광고·embed slot은 고정 크기 또는 min-height.
- font swap으로 인한 shift: `next/font` `display: 'swap'` + size-adjust.
- 동적 inject(banner, toast) 시 layout 안 누르도록 fixed/absolute + 적절 위치.

## rule (bundle 최적화)
- bundle analyzer 정기 점검: `@next/bundle-analyzer`.
- chunk per route (Next 자동) + dynamic import.
- third-party는 가벼운 대안 우선 (lodash → 단일 함수 import, moment → date-fns).
- tree-shaking 가능한 named import.
- polyfill은 필요한 것만.
- CSS는 critical 추출 + 비critical defer.

## rule (image 최적화)
- `<Image>` (next/image) 사용. `<img>` 직접 X.
- format: AVIF > WebP > JPEG (Next 자동 negotiation).
- responsive `sizes` 명시.
- placeholder='blur' (LQIP).
- 외부 도메인은 `images.remotePatterns` + optimization 활성.

## rule (font 최적화)
- `next/font/google` 또는 `next/font/local`. self-host (no FOIT).
- variable font 우선 (multi-weight 1 file).
- `display: 'swap'` (FOUT 허용 — 빈 화면 < 깜박임).
- subset 활성 (Korean + Latin).

## rule (data fetching 최적화)
- server fetch는 cache 명시 (`next: { revalidate, tags }`).
- TanStack Query staleTime 적절 (불필요 refetch X).
- prefetch는 server에서 (`prefetchQuery` + `HydrationBoundary`).
- waterfall 회피: 병렬 fetch (`Promise.all`).
- N+1 client fetch 회피 (server에서 join 또는 single query).

## rule (Suspense·streaming)
- 큰 페이지는 의미 단위로 Suspense boundary (header → main → footer 독립 스트리밍).
- loading.tsx로 page 단위 fallback.
- React 19 + Next 15: PPR (Partial Prerendering) 옵션 평가.

## rule (caching)
- HTTP cache header: `Cache-Control: public, max-age, s-maxage, stale-while-revalidate`.
- CDN cache (Vercel edge / Cloudflare).
- ISR (`revalidate: <sec>`) 정적 우선.
- mutation 후 `revalidatePath` / `revalidateTag`.

## rule (측정)
- 개발: Lighthouse + Chrome DevTools Performance + WebPageTest.
- 운영: RUM (Vercel Analytics / Datadog / Sentry).
- 회귀: PR마다 bundle size diff + Lighthouse CI.

## forbidden
- `<img>` 직접 (next/image)
- font를 외부 origin에서 직접 (FOIT + 외부 latency)
- 모든 페이지가 dynamic SSR (정적 가능하면 static)
- bundle analyzer 측정 없이 큰 lib 추가
- LCP element에 lazy load (priority 누락)
- CLS 0.1 초과 (image/embed 크기 누락)
- main thread blocking script (`<script>` head 동기)
- third-party를 비동기 없이 head에 (`<script async>` 또는 `next/script`)
- font swap 없이 (FOIT — 빈 화면)
- API route를 dynamic SSR로 무분별 호출 (캐시 활용)
- CDN cache 무시 (`cache: 'no-store'` 남발)
- RUM 없이 prod 운영
- Lighthouse CI 없이 회귀 측정 누락
- 큰 list를 가상화 없이 (1000+ row)
