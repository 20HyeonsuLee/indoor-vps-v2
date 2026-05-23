## rule
- 재사용 가능 로직은 custom hook으로 추출. 명명은 `use<Name>` (camelCase).
- 위치:
  - 도메인 종속: `src/features/<feature>/hooks/use<Name>.ts`
  - 전역 공통: `src/hooks/use<Name>.ts`
- 1 hook = 1 책임. 너무 많은 책임이면 분리.
- hook 안에서만 다른 hook 호출. component 또는 일반 함수에서 직접 호출 금지(rules of hooks).
- 조건문·반복문 안에서 hook 호출 금지. 항상 같은 순서로.
- 의존성 배열은 정확히. `react-hooks/exhaustive-deps` lint 활성.
- side effect는 `useEffect`. derived state는 계산값(useMemo 보수적)으로.
- async는 hook 안 함수 추출 (useEffect callback은 async 직접 X).
- server state는 `useQuery`/`useMutation` (tanstack-query) 추출. raw `useEffect + fetch` 금지.
- form state는 React Hook Form의 `useForm` 사용. 자체 form hook 만들지 X.
- 반환은 명확한 객체 또는 tuple. 너무 많이 반환하면 hook 분리.
- 테스트는 `@testing-library/react`의 `renderHook`.

## forbidden
- 조건문·반복문 안 hook 호출
- 일반 함수·class에서 hook 호출
- `useEffect` 의존성 배열 누락 또는 `[]`로 effect skip 회피
- async를 useEffect callback에 직접
- raw `fetch + useState + useEffect`로 server state 관리 (tanstack-query 사용)
- 한 hook이 server state + client state + form state 다 다룸 (분리)
- hook 이름이 `use`로 시작 안 함
- ref를 통한 상태 보유 (state로)
- 의존성에 객체·배열 직접 (useMemo로 안정화 또는 분해)
