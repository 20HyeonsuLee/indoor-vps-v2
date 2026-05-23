## rule (metadata)
- 모든 page는 metadata 정의. **static**(`export const metadata`) 또는 **dynamic**(`generateMetadata`).
- root `layout.tsx`에 default metadata (`metadataBase`, `title.template`, `description`, `openGraph.images` 기본).
- title은 `template: '%s | <Site>'` + page별 `title: '...'` (page 별 override).
- description은 페이지별 고유. 150~160자.
- canonical URL 명시 (`alternates.canonical`).
- 검색 노출 차단은 `robots: { index: false, follow: false }` 명시.

## rule (Open Graph + Twitter Card)
- `openGraph`: title, description, url, siteName, images (1200x630), type
- `twitter`: card='summary_large_image', images
- 이미지는 절대 URL. CDN 또는 public asset.
- og:image는 1.91:1 비율 + 1200x630 권장.

## rule (structured data / JSON-LD)
- 도메인 타입에 맞는 JSON-LD 추가 (`<script type="application/ld+json">`).
- 흔한 타입:
  - Article (블로그·뉴스)
  - Product (이커머스)
  - Organization (사이트 정보)
  - BreadcrumbList (네비)
  - FAQPage
- Next.js에서는 server component에 `<script>` 직접 또는 `next/script`.
- schema.org 명세 준수. Google Rich Results Test로 검증.

## rule (sitemap + robots)
- `app/sitemap.ts` (또는 `sitemap.xml`)로 sitemap 생성. dynamic route는 generate.
- `app/robots.ts` (또는 `robots.txt`)에 crawler 허용·차단.
- sitemap 50,000 URL 초과 시 index sitemap으로 분할.
- 변경 빈도(`changeFrequency`), 우선순위(`priority`) 의미 있게.

## rule (URL 구조)
- URL은 lowercase + kebab-case. underscore X.
- 트레일링 슬래시 정책 통일 (`trailingSlash: false` 기본).
- 동적 라우트(`[slug]`)는 의미 있는 slug. id만으로 X.
- 다국어 URL은 `/<locale>/<path>` 또는 도메인 분리.

## rule (이미지·미디어 SEO)
- `<Image>` (next/image) 사용. `alt` 필수 (a11y와 SEO 양쪽).
- 이미지 파일명도 의미 있게 (`hero-banner.jpg`).
- video는 transcript + structured data (`VideoObject`).

## rule (성능 = SEO)
- Core Web Vitals은 SEO 순위에 영향. `performance.md` 참조.
- LCP < 2.5s, INP < 200ms, CLS < 0.1.
- mobile-first 인덱싱: 모바일 viewport 우선.

## rule (HTTP)
- HTTPS 강제 (HSTS).
- 4xx/5xx 응답에 적절 status code (200 X — 검색엔진 혼란).
- redirect는 301 (영구) / 302 (임시) 의미 명확.
- broken link 0 (404 페이지 자체는 404 status로 응답).

## rule (검증)
- Google Search Console 등록 + sitemap 제출
- Lighthouse SEO 점수 ≥ 95
- Rich Results Test로 structured data 검증
- 정기적 crawl 분석 (broken link, duplicate content)

## forbidden
- metadata 누락 page (검색 노출 X)
- 모든 page에 같은 title/description (중복 → 검색 페널티)
- canonical 누락한 채 같은 콘텐츠가 여러 URL
- robots `noindex`를 prod에 실수 적용 (배포 직전 확인)
- og:image 누락 (SNS 공유 시 깨짐)
- structured data 잘못된 schema (Google 페널티)
- 404 page를 200으로 응답
- redirect chain 5+ (성능·SEO 손해)
- soft 404 (200 응답 + "찾을 수 없음" 텍스트 — 검색엔진은 인지 못)
- sitemap을 실시간 사용자 페이지로 (별 endpoint)
- dynamic OG image 매번 server에서 생성 (cache 활용)
- mobile viewport meta 누락 (`<meta name="viewport">`)
- placeholder text (`Lorem ipsum`) commit
