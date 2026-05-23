## rule
- Tailwind CSS 4+ (또는 3+). utility-first.
- 디자인 토큰은 `tailwind.config.{js,ts}`의 `theme.extend`에 정의. raw hex inline 금지.
- 색은 시맨틱 토큰 (`primary`, `success`, `danger`, `surface`). raw 색 hex 직접 사용 X.
- 간격·radius·shadow도 토큰화.
- class 순서는 **Prettier plugin (`prettier-plugin-tailwindcss`)**로 자동 정렬.
- 동적 class는 `clsx` 또는 `cn` util. template string `+` 결합 금지 (purge 깨짐).
- 반응형 prefix(`sm:`, `md:`, `lg:`) 모바일 우선 (base → 큰 화면).
- 다크모드는 `dark:` 변종 또는 CSS variable + media query.
- 컴포넌트 variant는 `class-variance-authority (cva)` 또는 직접 `cn` 패턴.
- 같은 class 묶음이 ≥ 3번 반복 → 컴포넌트로 추출 (utility 추상화 X).
- `@apply`는 최소. 디자인 시스템 base style 또는 vendor override 한정.
- arbitrary value (`[20px]`)는 토큰 없는 일회성만. 반복되면 토큰에 추가.

## forbidden
- raw hex 색 inline (`#FF0000` — 토큰으로)
- template string 으로 class 동적 생성 (`'p-' + size` — purge 깨짐)
- `style={{...}}` inline 남발 (토큰 우회)
- 동일 class 묶음 ≥ 3 반복 (컴포넌트 추출)
- `@apply` 남발 (utility 의미 사라짐)
- `!important` 남발 (`!`)
- `tailwind.config`에 raw 색 직접 (CSS variable 또는 토큰 alias)
- 디자인 시스템 토큰 없는 색·간격 사용
- class 순서 무질서 (prettier plugin 활성)
- responsive prefix 역순 (`lg:` 먼저 → 모바일 default 깨짐)
