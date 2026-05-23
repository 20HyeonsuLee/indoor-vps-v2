## rule
- Conventional Commits 준수. 형식: `<type>(<scope>): <subject>`.
- type: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf`, `style`.
- scope: Bounded Context 또는 layer (`mapping`, `scan`, `floor`, `infra`, `docs`).
- subject: 명령형, 소문자 시작, ≤ 72자, 마침표 없음.
- body는 선택. WHY 위주. WHAT은 diff에 있음.
- footer는 breaking change (`BREAKING CHANGE:`), 이슈 참조 (`Refs #123`, `Closes #45`), Co-Authored-By.
- 1 commit = 1 논리 변경. 무관한 변경 섞기 금지.
- 가능하면 작은 단위로 자주 commit. landing 시 squash merge로 1 PR = 1 commit.
- 한글 subject 허용하되 type/scope는 영문 유지 (`feat(mapping): 폴리곤 바인딩 추가`).
- WIP commit은 PR 직전 정리 (rebase -i 또는 squash 위임).
- pre-commit hook 실패 시 `--no-verify`로 우회 금지. 원인 해결 후 재시도.

## forbidden
- type 누락 또는 비표준 type (`update`, `change`, `fix-stuff`)
- subject에 마침표·이모지·이슈번호 직접
- 1 commit에 무관한 변경 묶기
- "WIP"·"fix typo"·"asdf" 같은 의미 없는 메시지 (squash 전이라도 검색성 X)
- subject > 72자
- body에 WHAT만 적기 (diff 중복)
- 비밀값(.env, key) 포함 commit
- amend로 published commit 변경
- `--no-verify` 우회
- 자동 생성 파일을 별도 commit 안 한 채 코드 변경에 섞기
