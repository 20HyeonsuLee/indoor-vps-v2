## rule (토큰)
- 모든 시각 값은 **토큰화**. raw hex·magic 간격 inline 금지.
- 토큰 분류:
  - **color**: primary/secondary/accent/success/warning/danger + surface/background/foreground/border + muted
  - **spacing**: 0/0.5/1/1.5/2/3/4/6/8/12/16/20/24 (4px grid)
  - **radius**: none/sm/md/lg/xl/full
  - **shadow**: sm/md/lg/xl + focus-ring
  - **typography**: font-family/size/line-height/letter-spacing/weight
  - **z-index**: dropdown(10)/sticky(20)/modal(30)/toast(40)
  - **motion**: duration(fast/normal/slow), easing
- 토큰 정의는 `tailwind.config.ts`의 `theme.extend` 또는 CSS variable.
- **CSS variable + Tailwind** 권장 (dark mode 전환 용이):
  ```css
  :root { --color-primary: 220 90% 56%; }
  .dark { --color-primary: 220 90% 70%; }
  ```
  ```ts
  // tailwind.config.ts
  colors: { primary: 'hsl(var(--color-primary) / <alpha-value>)' }
  ```
- 시맨틱 명명 (`primary`, `surface`, `danger`) > 색 명명 (`blue-500`, `red-700`).
- 단계 (`-50`/-100/.../-900)는 design lib 기본값 그대로 또는 audit 후.

## rule (theme)
- light + dark 2 theme 기본. user 선택은 `localStorage` (영속) + system pref(`prefers-color-scheme`) fallback.
- theme toggle 컴포넌트 1개 (디자인 시스템 ui에 둠).
- SSR FOUC 방지: html에 theme class를 head 안 inline script로 미리 set.
- 색 contrast는 양쪽 theme 모두 WCAG AA (a11y.md).

## rule (컴포넌트 라이브러리)
- 추천 lib (도입은 ADR):
  - **shadcn/ui** (코드 owned, Radix 기반) — 권장
  - **Radix UI** primitives (headless, a11y 완성)
  - **Headless UI**, **Ariakit** 대안
- Radix는 a11y·키보드 네비 검증된 primitives. 스타일은 Tailwind로.
- 외부 lib 도입 시 ADR + 의존성 정책 (`build/dependencies`).
- 자체 디자인 시스템은 `src/components/ui/`에 작성 (workflows/add-component).

## rule (variant 패턴)
- variant 정의는 **class-variance-authority (cva)** 권장:
  ```ts
  const buttonVariants = cva('base-class', {
    variants: { size: {...}, variant: {...} },
    defaultVariants: { size: 'md', variant: 'primary' },
  });
  ```
- prop API는 explicit `'sm' | 'md' | 'lg'`. boolean prop 폭증 X.
- composition > configuration. 너무 많은 variant면 컴포넌트 분리.

## rule (motion / animation)
- 단순 transition은 Tailwind (`transition-* duration-* ease-*`).
- 복잡 animation은 **Framer Motion** 또는 CSS keyframes.
- `prefers-reduced-motion` 존중 (motion 비활성 또는 단순화).
- animation duration: micro(100ms), default(200ms), slow(400ms). 1초 초과 X.
- 의미 있는 motion만 (사용자 attention guide). decorative motion 최소.

## rule (icon)
- 통일된 icon set (Lucide 권장, 또는 Heroicons / Phosphor).
- 1 페이지 안 여러 icon set 혼용 X.
- icon size 토큰화 (sm: 16px, md: 20px, lg: 24px).
- icon-only 버튼은 `aria-label` 필수 (a11y.md).

## rule (typography)
- font scale: xs/sm/base/lg/xl/2xl/3xl/4xl (Tailwind 기본 또는 design audit).
- heading 계층 (h1 → h6) 의미 단위 (시각 크기 X — class로 분리).
- line-height: body(1.5), heading(1.2~1.3), code(1.4).
- letter-spacing: heading만 약간 tight.

## rule (다국어 typography)
- 한글: 적절 line-height (1.6+), letter-spacing 좁게.
- font fallback: `system-ui, -apple-system, ...` 또는 web font + fallback.

## forbidden
- raw hex 색 inline (토큰만)
- magic 간격 (`px-[13px]`) — 토큰 사용
- 디자인 시스템 토큰 없이 새 색 추가 (PR description + 디자이너 합의)
- 1 페이지 다중 icon set 혼용
- 1 컴포넌트의 variant prop 5개 초과 (composition 검토)
- dark mode 미지원 prod 출시 (기본 사양으로 다크 우선)
- FOUC theme switch (head script로 inline init)
- contrast WCAG AA 미달 색 사용
- 1초 초과 animation
- `prefers-reduced-motion` 무시한 강제 motion
- shadcn/ui 컴포넌트를 무분별 install (필요한 것만 owned)
- theme switch가 page reload 유발 (SSR + client hydration 호환)
