## rule
- 사내 standard **template (.dotx)**을 SSOT로 보관.
- template 포함:
  - **style sheet** (제목·본문·인용·캡션·각주 등)
  - **theme** (색·font·effect)
  - **머리말·꼬리말** (회사 로고·페이지)
  - **표지 templates** (보고서·제안서·메모)
  - **placeholder** (제목·작성자·날짜)
  - **자동 텍스트** (반복 사용 문구 — building block)
- 문서 종류별 template:
  - `report.dotx` — 보고서
  - `proposal.dotx` — 제안서
  - `memo.dotx` — 메모
  - `manual.dotx` — 매뉴얼
  - `letter.dotx` — 공문
  - `academic.dotx` — 논문
- 새 문서는 template에서 시작 (`파일 > 새로 만들기 > 개인`).
- template 변경은 사내 공유 폴더 SSOT 갱신 + 변경 이력 기록.
- 사용자 개별 수정은 그 문서에만 적용 (template 수정 X).

## rule (template 위치)
- 사내 공유: SharePoint/OneDrive `Templates/` 폴더.
- 개인 PC: `<Word> > 파일 > 옵션 > 저장 > 기본 개인 templates 위치`.
- 매크로 포함은 `.dotm`. 보안 합의 후.

## rule (template 변경)
- 변경 owner: 디자이너 또는 ops.
- 변경 시 모든 사용자 공지.
- 기존 문서 retroactive 적용 안 됨 (다른 문제).
- breaking 변경은 ADR 또는 큰 공지.

## forbidden
- template 없이 매번 처음부터
- template 수정을 개인이 (사내 SSOT 깨짐)
- 다른 회사 template 무단 사용
- template에 sensitive 정보 (예시 데이터·이름)
- template 수정 후 공유 누락
- 1 문서 종류에 multiple template 사용 (한 종류 1 template)
- macro 포함 template (`.dotm`) 보안 합의 없이 배포
- template 안 사용자별 default 다름 (한 SSOT)
- 옛 template (변경 전) 계속 사용
