<!--
PR title은 Conventional Commits 형식: `<type>(<scope>): <subject>`
type: feat / fix / refactor / chore / docs / test / build / ci / perf
scope: Bounded Context 또는 layer (mapping, scan, floor, infra, docs)
-->

## Summary
<!-- 1~3 bullet. 변경의 핵심. -->
-

## Why
<!-- 동기/배경. 관련 PRD/ADR/issue를 link. -->
- Closes #
- Refs ADR-

## Changes
<!-- 주요 변경 영역. layer/패키지 단위. 선택. -->
-

## Test Plan
<!-- 어떻게 검증했는가. 단위/Karate/수동. 측정값 있으면 포함. -->
- [ ] `./gradlew test`
- [ ] `./gradlew karateTest`
- [ ] 수동 시나리오:

## Migration / Breaking
<!-- DB migration, config 변경, breaking change. 없으면 "없음". -->
- migration: 없음
- breaking: 없음

## Checklist
- [ ] PR title = Conventional Commits 형식
- [ ] 1 PR = 1 논리 변경 (무관 변경 없음)
- [ ] Flyway migration ≤ 1 (DDL/백필 분리 — CLAUDE.md)
- [ ] 비밀값/내부 URL 노출 없음
- [ ] 문서/ADR/glossary 동기화 필요 시 반영
