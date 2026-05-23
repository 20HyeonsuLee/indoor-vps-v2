## rule
- font family는 **2종 이하** (heading + body. mono는 코드 슬라이드만).
- sans-serif 권장 (스크린 가독). PowerPoint 안전 폰트: Calibri, Arial, Segoe UI, 한글 맑은 고딕/Pretendard.
- 회사 brand font 있으면 그것 (없으면 system safe).
- 크기 (16:9 1920x1080 기준):
  - **deck title**: 44~60pt
  - **slide headline**: 28~40pt
  - **body**: 18~24pt
  - **caption/footer**: 12~14pt
  - **최소**: 18pt (그 이하 가독 X)
- weight: heading bold, body regular. light는 큰 글씨에만.
- line height: heading 1.2, body 1.4~1.5.
- letter spacing: heading 약간 tight (-1~0), body normal.
- 한 슬라이드 안 font weight 3종 이하 (regular/bold/한 가지 더).
- 색은 design/color.md 토큰. 본문 dark on light 또는 light on dark 일관.
- 영문·한글 혼용 시 한글 폰트 우선 (영문도 같이 잘 보이게).
- mono font는 코드/숫자 정렬 한정.

## forbidden
- font family 3종 이상 혼용
- 본문 < 18pt
- font가 brand color와 contrast 미달 (a11y)
- decorative font를 본문에 (script, handwriting)
- italic 남발 (강조 목적 외)
- ALL CAPS 긴 텍스트 (단어 강조에만)
- underline (link 외 — 링크와 혼동)
- 글자에 shadow/outline/3D (가독 ↓)
- font fallback 미설정 (다른 PC에서 깨짐 — embed 또는 system safe)
- 한글·영문 폰트 mismatch (높이·간격 깨짐)
