## rule
- React 19+. 함수형 컴포넌트 + hooks만.
- TypeScript strict. 모든 props·state·hook 반환 타입 명시.
- 컴포넌트는 작게 (≤ 150줄). 큰 컴포넌트는 sub-component 또는 hook 추출.
- prop drilling 3 depth 초과는 store/context로.
- key는 안정 식별자(`id`). index는 정렬·삭제 없는 list만.
- `useMemo`/`useCallback`은 측정 후 추가. 무분별 적용은 readability ↓.
- `useEffect` 사용 신중. derive 가능하면 계산값, server data는 TanStack Query.
- React 19 기능:
  - `use(promise)` 패턴 (server component data 동기 read)
  - `Actions` (form action with transition)
  - `useOptimistic` (낙관 업데이트)
- error boundary는 `app/error.tsx` (Next) 또는 React Error Boundary.
- suspense boundary는 의미 단위 (page·section).
- ref는 `useRef` + React 19의 ref-as-prop. 명령형 호출 최소.

## forbidden
- class component
- `useEffect` + raw fetch + useState로 server state 관리 (TanStack Query)
- React 17 이전 패턴 (`React.FC` 강요·legacy lifecycle)
- conditional hook 호출
- `useMemo`/`useCallback`을 모든 prop·callback에 무분별
- `setState` callback 안에서 다른 setState 무한 chain
- key={index}로 reordering 가능 list 렌더
- effect 안 setState 의존성 누락으로 infinite loop
- ref로 state 보유 (re-render 안 됨)
- `React.memo` 무분별 (얕은 비교 비용 + props 안정성 안 보장이면 무효)
- `props.children`을 mutation
