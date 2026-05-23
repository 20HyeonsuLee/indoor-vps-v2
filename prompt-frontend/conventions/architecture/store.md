## rule
- 상태는 **4 종류로 분리**:
  - **server state**: API 데이터. TanStack Query (`useQuery`/`useMutation`)
  - **URL state**: 페이지·필터·검색어. Next router/searchParams
  - **form state**: 폼 입력. React Hook Form (`useForm`)
  - **client state**: UI 상태(모달 open, 토스트). useState 또는 Zustand
- 어떤 상태인지 먼저 분류 후 도구 선택. 한 데이터를 두 store에 두지 X.
- **Zustand**는 cross-component 공유가 필요한 client state만. 1 컴포넌트 한정이면 useState.
- store 위치: `src/stores/<name>Store.ts`. 1 store = 1 도메인.
- store 정의는 `create<<State>>()(...)` 형식. selector 사용 (`useStore(s => s.field)`)으로 불필요 re-render 방지.
- 영속화 필요 시 `persist` middleware (localStorage). 단 sensitive 데이터 X.
- server state를 Zustand에 복사 X (TanStack Query가 SSOT).
- 전역 상태 최소화. 컴포넌트 트리에서 prop으로 충분하면 prop.

## forbidden
- 상태 종류 혼동 (server state를 Zustand에, URL state를 useState에)
- 한 데이터를 두 store에 복제 (sync 비용 + drift)
- Zustand로 server data 관리 (TanStack Query 사용)
- store 안 비즈니스 로직 (변환만, 도메인 결정은 selector 또는 별도 함수)
- store에 sensitive 데이터 평문 (token·password 등)
- 전체 store를 통째 subscribe (`useStore(s => s)`) — selector로 좁히기
- Context API로 빈번 변경 상태 공유 (provider re-render 폭증 — Zustand 사용)
- 1 컴포넌트 한정 상태를 Zustand로 (useState로)
- store 명명 `*State` 또는 `*Slice` 외 모호한 이름
- store에 ref·instance method (직렬화 가능 데이터만)
