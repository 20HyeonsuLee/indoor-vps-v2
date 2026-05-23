## rule
- `.dockerignore`는 repo 루트와 `docker/` 양쪽에 둘 수 있다. build context의 `.dockerignore`가 우선.
- 다음 카테고리는 무조건 제외:
  - VCS: `.git/`, `.gitignore`, `.gitattributes`
  - 빌드 산출물: `build/`, `out/`, `target/`, `dist/`, `*.jar` (final stage에서 명시 COPY)
  - 캐시: `.gradle/`, `.cache/`, `node_modules/`, `__pycache__/`, `*.pyc`
  - IDE: `.idea/`, `.vscode/`, `*.iml`
  - 비밀: `.env`, `.env.*`, `credentials*.json`, `*.pem`, `*.key`
  - 문서/메타: `*.md`, `docs/`, `prompt/`, `LICENSE` (런타임 불필요)
  - 테스트: `src/test/`, `karate-config.js`
  - OS: `.DS_Store`, `Thumbs.db`
- 필요한 파일은 명시 allowlist 가능 (`!CHANGELOG.md`).
- ignore 후 build context 크기 < 100MB 권장. 초과 시 누락 항목 점검.
- `.dockerignore` 변경 시 다음 build에서 layer cache 다 깨질 수 있음. PR description에 명시.

## forbidden
- 비밀값 파일을 ignore 누락 (`.env`, `*.pem` 등 — 이미지에 leak)
- `src/test/` ignore 누락 (이미지 비대 + 의도 외 코드 포함)
- 전체 무시(`*`) 후 allowlist만 (의도 추적 어려움)
- `.git/` ignore 누락 (이미지 비대 + 메타 leak)
- `node_modules/` 같은 의존성 디렉토리 ignore 누락
- ignore에 production 필수 파일 포함 (의도치 않게 빠지면 runtime fail)
- repo root 외에 `.dockerignore` 없이 sub-directory에서 build (의도 외 컨텍스트 포함)
