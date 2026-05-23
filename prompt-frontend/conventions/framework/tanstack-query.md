## rule
- server state는 **TanStack Query** (`@tanstack/react-query`) 일관 사용. raw `useEffect + fetch` 금지.
- `QueryClient`는 root provider 1개. SSR 시 hydration 패턴 (`HydrationBoundary`).
- query는 **query key factory** 패턴. `src/features/<feature>/api/queryKeys.ts`:
  ```ts
  export const userKeys = {
    all: ['users'] as const,
    list: (filters: Filters) => [...userKeys.all, 'list', filters] as const,
    detail: (id: string) => [...userKeys.all, 'detail', id] as const,
  };
  ```
- query function은 api-client 호출 (`architecture/api-client.md`).
- mutation 성공 후 관련 query `invalidateQueries`. cache 수동 set은 낙관 업데이트만.
- staleTime / gcTime 기본:
  - staleTime: 30초 (도메인별 조정)
  - gcTime: 5분
- retry는 기본값 신뢰. 도메인별 조정 (`retry: false` for mutation).
- error는 `useQuery`의 `error` 객체로. component에서 분기.
- suspense 사용 시 `useSuspenseQuery` (Next 15와 호환).
- prefetch는 server component에서 `prefetchQuery` 후 `HydrationBoundary`로 전달.
- DevTools는 dev 환경만 (`process.env.NODE_ENV !== 'production'`).

## forbidden
- `useEffect + fetch + useState`로 server state 관리
- query key string 직접 (`['user', id]` — factory 사용)
- mutation 후 `refetch`로 모든 query 재요청 (`invalidateQueries`로 좁힘)
- `staleTime: Infinity`로 영구 cache (의도된 경우만)
- query 안 비즈니스 로직 (변환은 selector 또는 view layer)
- mutation 결과를 다른 query cache로 set without invalidate
- 컴포넌트마다 다른 query key (factory로 통일)
- prod에 DevTools 노출
- query function에 raw fetch (api-client 경유)
- 같은 query를 다른 staleTime으로 호출 (key가 같으면 cache 충돌)
