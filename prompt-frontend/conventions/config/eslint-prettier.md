## rule (eslint)
- ESLint flat config (`eslint.config.js`).
- preset:
  - `eslint:recommended`
  - `@typescript-eslint/recommended` + `recommended-type-checked`
  - `next/core-web-vitals`
  - `react-hooks/recommended` (rules of hooks 강제)
  - `jsx-a11y/recommended` (접근성)
- 추가 plugin:
  - `eslint-plugin-tailwindcss` (class 순서)
  - `eslint-plugin-import` (import 순서)
  - `eslint-plugin-unused-imports` (자동 제거)
- 핵심 rule:
  - `react-hooks/exhaustive-deps`: error
  - `@typescript-eslint/no-explicit-any`: error
  - `@typescript-eslint/no-floating-promises`: error
  - `import/no-default-export`: error (단 Next route 파일 예외)
- lint 실행: `pnpm lint` (`next lint --dir src --dir app`).
- CI에서 lint fail → PR merge X.

## rule (prettier)
- Prettier로 포맷 자동화. ESLint는 lint, Prettier는 format 역할 분리.
- 설정 `.prettierrc`:
  - `"semi": true`
  - `"singleQuote": true`
  - `"trailingComma": "all"`
  - `"printWidth": 100`
  - `"tabWidth": 2`
  - `"useTabs": false`
- plugin: `prettier-plugin-tailwindcss` (class 정렬).
- 통합: `eslint-config-prettier` (ESLint와 충돌하는 포맷 rule off).
- pre-commit hook: lint-staged + husky 또는 lefthook (git/hooks.md).

## forbidden
- `// eslint-disable` 남발 (이유 명시 필수)
- Prettier 미적용 commit
- `any` 허용 (도입 시 disable 코멘트 + 이유)
- floating promise (`await` 또는 `void` 명시)
- import 순서 무질서
- `default export` 무분별 (named export 우선, route는 예외)
- prettier + ESLint 포맷 충돌 (prettier 우선)
- format on save 비활성 (개발 흐름 깨짐 — IDE 설정 권장)
- CI에서 lint fail 무시
- prettier 설정 파일 여러 개 (`.prettierrc` 단일)
