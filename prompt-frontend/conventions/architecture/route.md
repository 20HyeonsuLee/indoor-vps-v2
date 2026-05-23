## rule
- 라우팅은 **Next.js App Router**(`app/`). Pages Router 신규 도입 X.
- 각 라우트 폴더에 `page.tsx`(필수), `layout.tsx`(선택), `loading.tsx`, `error.tsx`, `not-found.tsx`.
- 라우트 종속 컴포넌트는 `app/<route>/_components/`. underscore prefix는 Next 라우트 무시.
- 동적 라우트: `[id]` (필수), `[[...slug]]` (catch-all optional).
- group: `(group-name)/` — URL 영향 없는 묶음.
- **page는 server component 기본**. 데이터 fetch는 page 또는 layout에서 `await`.
- `"use client"`는 interaction 필요한 leaf 컴포넌트에만.
- metadata는 `export const metadata` 또는 `generateMetadata`. SEO 직결.
- search params는 page props `searchParams`로 받음. URL state로 관리.
- redirect는 `redirect()` (server) 또는 `useRouter().push()` (client).
- 인증 보호: middleware에서 `request.cookies` 확인 → `redirect`. 또는 layout에서 `await` 확인.
- error boundary는 `error.tsx`. unrecoverable은 `global-error.tsx`.

## forbidden
- Pages Router (`pages/`) 신규 추가
- page에 `"use client"` 부착 후 data fetch (server fetch 못함)
- route 안 직접 fetch + useState (server component 또는 tanstack-query 사용)
- 같은 polling/사용자 입력 분기를 route에 hardcode (URL state 활용)
- 인증 검사를 client만 의존 (middleware 또는 server layer)
- secret을 client component env로 노출 (`NEXT_PUBLIC_` prefix 의미 확인)
- 라우트 깊이 5 단계 초과 (group으로 정리)
- error.tsx 누락 (uncaught 시 hard error)
- redirect chain 안에 외부 도메인 (open redirect)
