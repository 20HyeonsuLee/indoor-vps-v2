## 목적
review 통과 후 최종화 + export + 공유.

## 절차
1. **최종 수정 반영**
   - review 코멘트 다 처리 (blocker 0)
   - sign-off 확인
2. **최종 점검**
   - `quality/review.md` 체크리스트 풀 (특히 typo·날짜·페이지번호)
   - 폰트 embed 확인 (다른 PC 호환)
   - 이미지 압축 (파일 크기 줄임)
   - 외부 link 동작 (절대 X, 상대 또는 cloud)
3. **저장**
   - `.pptx` 최종 파일명: `<topic>_<audience>_<YYYY-MM-DD>_final.pptx`
   - cloud (OneDrive/SharePoint)에 저장 + 버전 history 활용
4. **export**
   - PDF (공유용): 슬라이드만 또는 노트 포함
   - PNG/EMF (필요 시 individual slide)
   - convention: `office/export`
5. **메타데이터**
   - 제목·작성자·키워드 설정
   - 비밀 정보 없는지 검사
6. **보안 검토**
   - sensitive 정보 마스킹
   - 비밀번호 (sensitive deck만)
   - 인쇄·복사 차단 (sensitive PDF)
7. **공유**
   - 공유 방법:
     - email + PDF (작은 deck)
     - cloud link (큰 deck)
     - 사내 공유 폴더
   - 공유 message에 핵심 메시지 1줄 요약
8. **backup**
   - cloud (primary)
   - 본인 email (secondary)
   - USB (발표 환경)
9. **archive**
   - 사내 공유 폴더에 final 보관
   - source `.pptx` + export PDF 같이

## 산출물
- final `.pptx`
- final PDF (공유용)
- backup 3 (cloud·email·USB)

## 금지
- review 통과 안 한 채 final
- typo·날짜 오류 잔존
- 폰트 embed 누락
- 외부 link 절대 경로
- sensitive 정보 노출
- 메타데이터 누락
- single backup
- 발표 직전 export (사전 dry-run + export)
- final 후 자유 수정 (변경 시 재 review)
- archive 누락 (다음 deck 재사용 어려움)
