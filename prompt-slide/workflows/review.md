## 목적
draft를 6 axis로 검토. `quality/review.md` 체크리스트 실행.

## 단계
1. **author self-review** (draft 직후, 같은 날 또는 24h 후)
   - 모든 슬라이드 5초 rule 통과 확인
   - 6 axis self-check (message·flow·시각·디자인·a11y·품질)
   - 발표 노트 같이 점검
2. **peer review** (≥ 1명, 외부 발표는 ≥ 2명)
   - 6 axis 검토 + 의견
   - 코멘트는 댓글 또는 별 문서 (직접 슬라이드 수정 X)
   - prefix: `nit:` / `q:` / `suggest:` / `blocker:`
3. **stakeholder review** (필요 시)
   - 결정권자·디자이너·법무·security·marketing
   - 변경 종류별: brand → 디자이너, 매출 데이터 → finance, 외부 공유 → 법무
4. **author 응답** (24h 권장)
   - blocker는 수정 또는 합의
   - suggest는 author 판단
   - 변경 후 reviewer 재확인
5. **dry-run** (큰 발표 — conference·executive)
   - 실제 timing 측정 (1분/슬라이드 가이드 검증)
   - Q&A 시뮬레이션
   - 청중 시뮬레이션 (지인·동료)

## 검토 시 흔한 issue
- headline이 결론 아님 (주제만)
- 1 슬라이드 2+ message
- chart 출처·단위 누락
- contrast WCAG 미달
- 폰트 깨짐 (다른 PC)
- 슬라이드 번호 일관 X
- 약어 풀이 누락
- backup이 본문에 섞임
- sensitive 정보 노출

## 산출물
- 검토 코멘트·이슈 list
- 수정된 draft (draft2, draft3 ...)
- review 통과 sign-off

## 금지
- self-review 없이 peer로 직행
- peer review 0회 외부 공유
- blocker 미해결 다음 단계
- review 코멘트 묵살
- review 통과 후 큰 변경 (재검토 필요)
- 직접 슬라이드 수정으로 review (충돌·trail 손실)
- dry-run 없이 큰 발표
- 검토자 의견 익명 처리 (책임 추적 어려움)
