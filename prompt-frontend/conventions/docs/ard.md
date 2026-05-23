## rule
- ADR(Architecture Decision Record)은 `docs/decisions/` 폴더에 둔다.
- 파일명은 `NNNN-<kebab-case-title>.md` (4자리 zero-pad, 예: `0013-floor-area.md`).
- 번호는 단조 증가. 빈 번호 X. 폐기된 ADR도 번호 유지.
- 각 ADR은 1 결정 단위. 여러 결정 묶기 금지.
- 본문 구조:
  - `# NNNN. <Title>`
  - **Status**: Proposed / Accepted / Deprecated / Superseded by NNNN
  - **Context**: 결정이 필요해진 배경, 제약, 트레이드오프
  - **Decision**: 채택한 안 (1~3문장)
  - **Consequences**: 따라오는 변화·비용·후속 작업
  - **Alternatives Considered**: 검토 후 기각한 안과 기각 이유
- 결정 변경 시 기존 ADR을 수정하지 않고 **새 ADR 작성 + Status를 Superseded로 변경**.
- ADR 추가 시 `_project_meta.decisions` 인덱스(CLAUDE.md)에 ID·title·rationale 1줄 추가.
- "왜"가 핵심. WHAT은 코드/스키마에 있음. ADR은 WHY를 보존한다.
- 결정에 영향 받는 코드/문서에 ADR ID로 역참조 (`(ADR-008)` 식 인용).

## forbidden
- 같은 ADR을 후속 수정으로 의미 변경 (새 ADR + Superseded)
- 1 ADR에 여러 결정 묶기
- 번호 재사용/건너뛰기
- "이렇게 했다" 식 WHAT 위주 기술 (WHY 필수)
- Alternatives 섹션 생략 (대안 검토 흔적 없는 결정 = 근거 부족)
- ADR을 PR 본문으로 대체 (영구 기록은 ADR로)
- ADR 작성 없이 ADR 본질 결정(layer/persistence/communication 등) 진행
- 이미 Accepted된 ADR을 silent edit (변경은 새 ADR로)
