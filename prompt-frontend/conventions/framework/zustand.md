## rule
- Zustand는 **cross-component 공유 client state**만. server state는 TanStack Query, form state는 RHF.
- store 위치: `src/stores/<name>Store.ts`. 1 store = 1 도메인.
- 정의 패턴:
  ```ts
  type State = { ... };
  type Action = { ... };
  export const useUiStore = create<State & Action>()((set, get) => ({
    ...
  }));
  ```
- selector 사용 강제 (`useUiStore(s => s.field)`). 전체 store subscribe 금지.
- shallow 비교: `useUiStore(selector, shallow)` 다중 필드 선택 시.
- middleware:
  - `persist`: localStorage 영속 (sensitive 데이터 X)
  - `devtools`: dev 한정
  - `immer`: nested 업데이트 시 가독성
- action은 store 안 함수로 정의. 외부에서 `getState`로 호출 금지 (test 어려움).
- 초기값과 reset 함수 명시 (`useStore.setState(initial, true)` 또는 `reset()` action).
- store는 직렬화 가능 데이터만. ref·class instance 금지.
- SSR 환경에서는 client provider로 감싸기 (next/dynamic 또는 client component root).

## forbidden
- server state를 Zustand에 저장 (TanStack Query 사용)
- form state를 Zustand에 저장 (React Hook Form 사용)
- 1 컴포넌트 한정 상태를 Zustand로 (useState로)
- 전체 store subscribe (`useStore(s => s)`) — selector 좁히기
- sensitive 데이터(token, password) persist
- store에 ref·class instance·function이 아닌 mutable 객체
- action 없이 외부에서 `setState` 호출 (encapsulation 깨짐)
- 1 store에 무관한 도메인 묶기 (분리)
- store가 다른 store 직접 import (cross-store 통신은 selector 조합 또는 명시적 의존)
- persist에 schema migration 누락 (구버전 데이터 호환 깨짐)
