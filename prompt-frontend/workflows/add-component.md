## 목적
디자인 시스템 컴포넌트(Button, Input, Modal 등 재사용 가능 UI primitive)를 추가하는 절차.

## 분류
| 분류 | 위치 | 예 |
|---|---|---|
| **ui primitive** | `src/components/ui/` | Button, Input, Modal, Tooltip, Dropdown |
| **composite** | `src/components/<category>/` | Card, DataTable, EmptyState |
| **feature-specific** | `src/features/<feature>/components/` | UserCard, PaymentForm — 본 워크플로우 X (add-feature) |
| **layout** | `app/<route>/layout.tsx` 또는 `src/components/layouts/` | AppShell, Sidebar |

본 워크플로우는 **ui primitive + composite** 한정.

## 절차
1. **디자인 토큰 확정**
   - 색·간격·radius·typography는 `tailwind.config`의 theme 토큰만 사용
   - 새 토큰 필요하면 디자이너와 합의 후 토큰 추가 PR (선행)
   - convention: `framework/tailwind`
2. **variant 정의** (필요 시)
   - `class-variance-authority (cva)` 또는 자체 cn 패턴
   - prop API 명시 (`size: 'sm' | 'md' | 'lg'`, `variant: 'primary' | 'secondary'`)
3. **컴포넌트 작성** (`src/components/ui/<Name>.tsx`)
   - forwardRef (또는 React 19 ref prop)로 DOM ref 노출
   - `displayName` 명시 (DevTools)
   - a11y 우선 (role·aria-*·키보드 네비)
   - convention: `architecture/component`, `framework/react`
4. **a11y 검증**
   - WCAG 2.1 AA 기준
   - 키보드 네비 (Tab/Shift+Tab/Enter/Esc)
   - focus visible (focus ring)
   - 스크린리더 (aria-label·role)
   - color contrast (4.5:1 이상)
5. **테스트** (Vitest + RTL)
   - 모든 variant 렌더
   - 인터랙션 (click·hover·focus·keyboard)
   - a11y 자동 (`jest-axe`)
   - convention: `framework/vitest`, `workflows/test`
6. **Storybook 또는 데모 페이지** (있다면)
   - variant별 stories
   - 디자인 시스템 페이지에서 사용 예시
7. **문서**
   - 컴포넌트 JSDoc (props 설명·예시)
   - 디자인 시스템 페이지 인용
8. **PR**

## a11y 체크리스트
- [ ] role / aria-* 적절
- [ ] 키보드만으로 모든 인터랙션 가능
- [ ] focus 시 시각 표시 (focus-visible)
- [ ] color contrast 4.5:1 이상 (텍스트)
- [ ] motion 비활성 옵션 (`prefers-reduced-motion`)
- [ ] 스크린리더 의미 전달 (label 없는 icon button → aria-label)

## 금지
- 디자인 토큰 무시 (raw hex·간격 inline)
- 1 컴포넌트 한정 prop API가 너무 많음 (5+ → composition 패턴)
- forwardRef 없이 ref 필요한 컴포넌트
- a11y 점검 없이 머지
- variant를 if/else로 인라인 (cva 또는 패턴화)
- 같은 책임을 여러 컴포넌트가 (`Button`, `IconButton`, `LinkButton` 무분별 — composition으로)
- 디자인 시스템 페이지 갱신 누락
- focus outline 제거 (a11y 위반)
- 키보드 trap (Modal에서 Esc 닫기·Tab cycle 보장)
- 모든 컴포넌트를 "use client" (가능하면 server)
