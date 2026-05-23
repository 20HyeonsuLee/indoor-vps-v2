## rule
- worktree는 task 단위 격리 작업 공간으로 사용한다. 
- worktree 위치는 `<repo>/.worktrees/<task-id>` 또는 `<repo>/../<repo>-<task-id>` 권장. main checkout 밖에 두기.
- 1 worktree = 1 branch = 1 task. cross-task 묶음 금지.
- worktree branch는 `feat/...`/`fix/...` 등 표준 명명 (branch.md 참조).
- 생성: `git worktree add <path> -b <branch>`.
- 사용 종료 시 commit/push → PR → squash merge → `git worktree remove <path>` + `git branch -D <branch>` (remote도 정리).
- 동일 task에 중간 중단 후 재개는 `checkpoint-resume` skill로 worktree 상태 재확인.
- worktree 안 dirty change는 함부로 reset/clean 하지 않는다. 보존 우선.
- worktree에서 main으로 직접 작업 금지 (main checkout은 read 전용 유지 권장).
- worktree 동시 사용은 host당 < 5개 권장. 초과 시 정리.

## forbidden
- 한 worktree에 무관한 task 섞기
- worktree branch를 main에서 직접 분기 외 (다른 worktree branch 위로 쌓기 금지)
- worktree 안 dirty change를 확인 없이 `git reset --hard`/`git clean -fd`
- worktree 제거 전 branch 강제 삭제 (`git branch -D`로 dangling commit 양산)
- main checkout과 같은 경로에 worktree 생성
- worktree path를 git 추적 (`.worktrees/`는 `.gitignore`)
- 장기 방치된 worktree (1주 초과 idle은 정리 검토)
- 다른 host의 worktree branch를 같은 이름으로 재사용 (혼동)
- worktree 안에서 `git worktree remove --force` 자기 자신 제거
