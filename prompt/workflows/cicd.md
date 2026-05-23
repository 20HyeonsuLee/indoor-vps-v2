## 목적
PR push부터 prod 배포까지의 CI/CD 파이프라인 절차. GitHub Actions 기반.

## 트리거 → 단계 매트릭스
| 트리거 | 단계 | 환경 영향 |
|---|---|---|
| feature/* push | lint + test + Karate | 없음 (검증만) |
| PR open/sync to main | lint + test + Karate + build + dry-run docker build | 없음 |
| **main merge (squash)** | lint + test + Karate + docker build + push to ghcr (`:<sha>`, `:dev` mutable) → **dev 자동 배포** | dev |
| **SemVer tag push (vX.Y.Z)** | dev에서 검증된 digest를 prod로 promote (`:vX.Y.Z`) → **prod 배포** | prod |
| hotfix tag push | 같은 prod 배포 흐름 + 사후 PR 의무 (workflows/hotfix.md) | prod |

## 단계 책임
1. **lint**: `./gradlew spotlessCheck`. 실패 시 즉시 fail (1초)
2. **unit test**: `./gradlew test`
3. **karate**: `./gradlew karateTest`
4. **build**: `./gradlew bootJar`
5. **docker build**: multi-stage. BuildKit cache + `--cache-from`/`--cache-to` registry cache
6. **push**: `ghcr.io/<owner>/<repo>/app:<sha>` (immutable) + `:dev`/`:prod` (mutable env pointer)
7. **deploy**: 호스트에서 `git pull && docker compose pull && docker compose up -d` (또는 GitHub Actions가 ssh로 실행)
8. **post-deploy verify**: smoke test (Karate `@smoke` tag) + healthcheck

## 정책
- workflow yaml ≤ 200 줄 (CLAUDE.md `scale_thresholds.workflow_yaml_lines`)
- job 수 ≤ 8 (초과 시 reusable workflow + composite action)
- secret은 GitHub Secrets만 (config/env.md)
- image promotion은 **digest 기반** (재빌드 X — docker/image.md)
- 모든 commit/tag는 signed (git/signing.md)
- branch protection: signed commit + status check + review 요구

## 분기 절차
- **DB migration 포함 배포**: Expand-Contract 2단계. (framework/flyway.md, workflows/migration.md)
- **base image 변경**: 별도 workflow trigger (docker/base.md)
- **rollback**: 이전 SemVer tag를 prod로 재 promote

## 게이트 (각 단계 fail 시)
- lint fail → 즉시 PR fail. format 적용 commit 권장
- unit test fail → PR merge X
- karate fail → PR merge X
- docker build fail → PR merge X
- push fail → retry 후 fail
- deploy fail → rollback (이전 digest 복구)
- post-deploy smoke fail → rollback + alert

## 부수 자동화 (선택)
- Dependabot: 의존성 PR 자동 생성 (build/dependencies.md, git/automation 대비)
- CodeQL: 보안 정적 분석
- Stale bot: 30일 idle 이슈/PR (git/issue.md)
