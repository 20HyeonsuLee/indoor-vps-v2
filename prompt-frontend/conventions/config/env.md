## rule
- 환경변수는 `.env.<env>` 파일 또는 호스트 주입.
- Next.js 규약:
  - `.env`: 모든 환경 default
  - `.env.local`: 로컬 override (`.gitignore`)
  - `.env.development` / `.env.production`: NODE_ENV별
  - `.env.test`: 테스트
- **`NEXT_PUBLIC_*` prefix만 client에 노출**. 그 외는 server-only (build 시 inline 안 됨).
- secret은 절대 `NEXT_PUBLIC_*`에 두지 않는다. (API key, DB password 등)
- 환경변수 schema는 Zod로 정의 (`src/env.ts`):
  ```ts
  export const env = createEnv({
    server: { DATABASE_URL: z.string().url(), ... },
    client: { NEXT_PUBLIC_API_BASE_URL: z.string().url() },
    runtimeEnv: process.env,
  });
  ```
  `@t3-oss/env-nextjs` 같은 lib 권장.
- 모든 코드는 `env` 객체 import. `process.env.XXX` 직접 접근 금지.
- 새 env 추가 시 `.env.example` 동시 갱신. 키 + 설명 + 예시 값.
- prod secret은 GitHub Secrets → CI → 호스트 주입. 빌드 시 inline 금지.
- runtime config (런타임 변경 필요) vs build-time config (빌드 시 inline) 구분.

## forbidden
- secret을 `NEXT_PUBLIC_*`로 노출
- `.env.local`/`.env*.local` commit
- `process.env.XXX` 코드 직접 (env 객체 경유)
- schema 없이 env 사용 (런타임 검증 누락)
- 빌드 image에 secret bake
- 같은 env 변수가 여러 이름 (`API_URL` vs `API_BASE_URL`)
- `.env.example` 갱신 누락
- 환경별 default 값을 base `.env`에 두기 (default 값 명시는 schema 또는 env-specific 파일)
- credentials를 git history에 남김 (`git filter-repo`로 정리 + key rotation)
- 비밀값을 build log에 출력 (`echo $SECRET`)
