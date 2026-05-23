## 목적
prod 장애·보안 취약점을 우선 복구하고 사후 절차를 채우는 흐름.

## 시작 trigger
- prod incident alert (5xx 비율·healthcheck fail·oncall page)
- 보안 취약점 P0/P1 (security/security.md 정책)
- 데이터 손상 의심

## 우선 순위
1. **복구 (stop the bleeding)**
2. **근본 원인 조사**
3. **재발 방지**
4. **사후 정리 (incident report·ADR)**

## 절차
1. **incident 선언**
   - GitHub Issue `bug(<scope>): <incident-title>` + label `prio:p0`
   - 영향 범위·시작 시각 기록
2. **rollback 우선 검토** (release.md rollback)
   - 이전 SemVer tag로 prod promote
   - rollback 가능하면 그게 가장 빠름
3. **rollback 불가 시 hotfix branch**
   - main에서 `fix/<incident-id>-<topic>` 분기
   - convention: `git/branch.md`
4. **최소 변경으로 fix**
   - 무관한 refactor 금지 (scope 좁게)
   - 단위 테스트 + Karate 회귀 시나리오 추가
5. **review 우회 정책**
   - 정상 절차 권장 (workflows/code-review.md)
   - **긴급 시** self-merge 가능하되 사후 review 의무 (24h 내)
6. **PR merge + 긴급 release**
   - SemVer PATCH bump (`vX.Y.Z+1`)
   - convention: `workflows/release.md`
   - tag push로 prod 자동 배포
7. **post-deploy 검증**
   - 5xx 비율·healthcheck·로그·메트릭 30분 모니터링
   - 재발 신호 즉시 rollback
8. **incident report 작성** (24h 내)
   - `docs/incidents/<date>-<topic>.md` 또는 issue 본문
   - timeline·근본 원인·영향·복구 조치·재발 방지 액션
9. **재발 방지 액션**
   - 테스트 추가
   - 모니터링·alert 보강 (observability/observability.md)
   - convention/룰 변경 시 PR (prompt/conventions/...)
   - 필요 시 ADR (`docs/ard.md`)
10. **사후 review**
    - hotfix PR을 정상 review 절차로 사후 검토
    - 무리뷰 부분 보강

## 24h 내 의무
- incident report 초안
- 사후 PR review 완료
- 재발 방지 액션 1개 이상 issue 등록

## 1주 내 의무
- 재발 방지 액션 구현 PR open
- ADR 또는 runbook 갱신 (필요 시)

## 금지
- hotfix를 main 우회 (모든 변경은 main 경유)
- 무관한 refactor 끼워넣기 (scope 좁게)
- incident report 누락
- 사후 review 누락
- "끝났으니 됐다" 종료 (재발 방지 액션 0)
- rollback 가능한 상황에 hotfix 강행 (rollback이 더 빠르고 안전)
- destructive migration을 hotfix에 포함 (별도 PR + Expand-Contract)
- secret 노출 incident에 secret rotation 누락
