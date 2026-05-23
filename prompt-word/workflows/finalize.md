## 목적
review 통과 후 최종화 + export + 공유 + archive.

## 절차
1. **변경 추적 정리**
   - 모든 변경 적용/거부
   - 추적 비활성
2. **댓글 정리**
   - 모두 해결 또는 삭제
3. **field update**
   - `Ctrl+A` → `F9` (목차·페이지번호·cross-ref 갱신)
4. **맞춤법·일관성 최종 점검**
   - F7 + 수동 검토
   - 용어집 vs 본문 일관 (`Ctrl+F` 검색)
5. **메타·hidden 검사**
   - `파일 > 정보 > 문서 검사`
   - 작성자·comment·revision·hidden text 검토
   - sensitive 정보 마스킹
6. **a11y 검사**
   - `검토 > 접근성 검사`
   - issue 해결
7. **저장**
   - `.docx` 최종 파일명: `<topic>_<doc-type>_<YYYY-MM-DD>_v<NN>.docx`
   - cloud (OneDrive/SharePoint) + version history
8. **export PDF**
   - 옵션:
     - 태그된 PDF (a11y)
     - 북마크 (heading 기반)
     - 비밀번호 (sensitive)
   - 파일명: 같은 패턴 + `.pdf`
9. **보안 설정**
   - sensitive: 비밀번호·편집 제한
   - 외부 공유: read-only PDF
10. **공유**
    - email + PDF (작은 문서)
    - cloud link (큰 문서)
    - 사내 공유 폴더
    - 공유 message에 핵심 요약 1줄
11. **archive**
    - cloud (primary)
    - 사내 공유 폴더 (final 보관)
    - source `.docx` + export PDF 같이
12. **backup**
    - cloud
    - 본인 email
    - (인쇄본 — 계약·법적 문서)

## 산출물
- final `.docx`
- final PDF
- archive (cloud + 공유 폴더)
- backup 3 (cloud · email · 인쇄)

## 금지
- review 통과 안 한 채 final
- 변경 추적·댓글 잔존
- field 미갱신 (목차 stale)
- 메타·hidden 정보 검사 누락
- sensitive 정보 노출
- a11y 검사 누락
- 폰트 embed 누락 (다른 PC)
- 비밀번호 미고지
- 단일 backup
- archive 누락
- 같은 final 여러 버전 (`_final2`, `_final_real`)
- 편집 가능 docx를 sensitive 외부 공유 (PDF + 보안)
