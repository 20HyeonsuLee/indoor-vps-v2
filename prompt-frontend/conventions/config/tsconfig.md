## rule
- TypeScript strict 모드 필수:
  - `"strict": true`
  - `"noUncheckedIndexedAccess": true`
  - `"noImplicitOverride": true`
  - `"noFallthroughCasesInSwitch": true`
  - `"forceConsistentCasingInFileNames": true`
- target/module은 Next 권장값 (`"target": "ES2022"`, `"module": "esnext"`, `"moduleResolution": "bundler"`).
- `"jsx": "preserve"` (Next.js가 transform).
- path alias 사용 (`"baseUrl": "."`, `"paths": { "@/*": ["src/*"] }`). 상대경로 `../../../` 회피.
- `include`/`exclude` 명시. `node_modules`, `.next`, `dist` 제외.
- 별도 tsconfig:
  - `tsconfig.json` (개발)
  - `tsconfig.build.json` (빌드, test 제외) — Next는 보통 1개로 충분
- `incremental: true` + `tsBuildInfoFile` (빌드 캐시).
- typed lint는 ESLint `@typescript-eslint`로.
- `any` 사용은 최소. 도입 시 `// eslint-disable-next-line` + 이유 코멘트.

## forbidden
- `"strict": false`
- `"noImplicitAny": false`
- `// @ts-ignore` 남발 (`@ts-expect-error` + 이유)
- `any` 무분별 (`unknown`으로 좁히기)
- type cast `as <Type>` 남발 (Zod parse 또는 type guard)
- `// @ts-nocheck` 사용
- 상대경로 `../../../*` (path alias 사용)
- `tsconfig.json` 다중 정의 충돌
- build 시 `typescript.ignoreBuildErrors: true` (next-config 참조)
- `Function` / `Object` / `{}` 타입 (구체 타입 명시)
