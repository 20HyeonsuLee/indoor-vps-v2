## rule
- **master slide**로 색·typography·layout·footer 표준화. 매 슬라이드 직접 그리지 X.
- master에 포함:
  - background (색 또는 image)
  - footer (로고·페이지번호·날짜)
  - placeholder (headline·body·visual)
  - 기본 색·font
- layout master 종류 (PowerPoint "슬라이드 마스터"):
  - title slide
  - section divider
  - headline + body
  - headline + 2 column
  - headline + visual
  - quote/callout
  - Q&A
- template (`.potx`)을 SSOT로 보관. 새 deck은 template에서 시작.
- template은 brand guide와 일치 (`branding/brand.md`).
- 다국어 deck은 한·영 master 동일 디자인, font fallback 다름 가능.
- 사내 공통 template은 회사 자산으로 관리 (디자인 팀과 sync).

## forbidden
- master 안 쓰고 매 슬라이드 직접 디자인
- master에 메시지·콘텐츠 (placeholder만)
- 1 deck에 2+ master 혼용 (의도적 변경 외)
- template 수정 후 versioning 누락
- brand guide 위반 색·font를 master에
- placeholder 무시하고 text box 직접 추가 (master 통제 깨짐)
- 같은 정보를 footer + 본문에 중복
- master 변경을 deck마다 산발 (template으로)
