## rule
- `.github/CODEOWNERS` 파일로 PR 자동 리뷰어 할당.
- 위치는 `.github/CODEOWNERS` (또는 repo root). 표준은 `.github/`.
- 패턴은 glob. 첫 매칭이 아니라 **마지막 매칭이 이김** (구체적 패턴을 아래에).
- owner는 `@<github-handle>` 또는 `@<org>/<team>`.
- 도메인/layer별 owner 분리 (`src/main/java/.../mapping/` → mapping 팀).
- 핵심 폴더는 owner 필수: `docs/decisions/`, `src/main/resources/db/migration/`, `.github/workflows/`, `docker/`, `prompt/conventions/`.
- 1인 프로젝트 단계에선 wildcard owner 1명. 협업자 추가 시 도메인별 분리.
- CODEOWNERS 변경은 그 자체로 owner 승인 필요 (메타 정책).
- branch protection rule과 같이 적용: "Require review from CODEOWNERS" 활성화 필요.

## forbidden
- CODEOWNERS 없이 핵심 폴더 (migration / workflows / decisions) merge
- 모호한 패턴(`*`)만으로 모든 변경을 한 사람에게 (병목)
- 비활성 계정 owner 지정
- branch protection rule 없이 CODEOWNERS만 두기 (강제력 X)
- CODEOWNERS 수정으로 리뷰 우회 (메타 owner 승인 필요)
- 외부 계정/봇을 owner로 지정 (책임 추적 불가)
