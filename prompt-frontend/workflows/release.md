## 목적
dev에서 검증된 image를 prod로 안전하게 promote. SemVer tag 기반.

## 모델 (git/release.md + docker/image.md 짝)
- dev 배포: main merge 자동 (`:<sha>` + `:dev` mutable pointer)
- prod 배포: SemVer tag push trigger
- promotion은 image digest 기반 (재빌드 X)

## 절차
1. **dev 검증**
   - main merge 후 자동 배포된 dev 환경에서 smoke test
   - Karate `@smoke` tag CI에서 실행
   - 수동 시나리오 (선택)
2. **변경 분류 → SemVer bump 결정**
   - MAJOR: breaking change (API contract 변경, DB destructive)
   - MINOR: 기능 추가 (역호환)
   - PATCH: 버그 수정, 내부 개선
   - footer `BREAKING CHANGE:` 또는 type `!` (`feat!:`)는 MAJOR 강제
3. **changelog 갱신**
   - release-please 또는 git-cliff로 자동 PR 생성
   - 수동 보강 (migration step, breaking notes)
   - convention: `git/changelog.md` (있다면) 또는 release.md 본문
4. **SemVer tag 생성**
   - `git tag -s vX.Y.Z -m "release vX.Y.Z"` (signed annotated)
   - convention: `git/signing.md`, `git/release.md`
5. **tag push**
   - `git push origin vX.Y.Z`
   - GitHub Actions가 prod 배포 trigger
6. **promote (CI 자동)**
   - dev에서 검증된 `:<sha>` digest를 `:vX.Y.Z` + `:prod`로 재태그
   - registry에서 manifest copy (이미지 재빌드 X)
7. **prod 호스트 배포**
   - ssh 또는 GitHub Actions가 호스트에서 `compose pull && compose up -d`
   - `APP_VERSION` env를 새 tag로 갱신
8. **post-deploy 검증**
   - smoke test
   - healthcheck
   - 로그·메트릭 5분 모니터링
9. **release note publish**
   - GitHub Release 생성 (자동 생성 본문 + 사람 검수)
   - migration step·breaking change 강조

## pre-release
- `vX.Y.Z-rc.N` 형식
- prod에 promote 가능 (단 별도 환경 또는 canary)
- 정식 tag는 별도

## rollback
- 이전 SemVer tag를 prod로 재 promote (`APP_VERSION=v0.3.0`)
- destructive migration이 있었으면 forward fix (workflows/migration.md rollback 정책)
- rollback도 ADR 또는 incident report

## hotfix는 별 workflow
- `workflows/hotfix.md`

## 금지
- main 외 branch에서 prod tag
- 같은 SemVer tag 재사용
- 수동 docker push로 prod 배포
- 재빌드로 promote (digest 다르면 검증 무의미)
- changelog 없이 tag push
- pre-release를 prod로 promote (별도 결정 필요)
- `--force` tag push
- breaking change footer 누락하고 minor bump
