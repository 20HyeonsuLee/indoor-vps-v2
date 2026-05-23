## rule (PDF 출력)
- 공유는 PDF default. pptx는 편집 필요할 때만.
- PDF 옵션:
  - 슬라이드 이미지 형식: PNG 또는 EMF
  - 노트 포함 vs 슬라이드만 (보통 슬라이드만)
  - 색공간: RGB (스크린) / CMYK (인쇄)
  - resolution: 고해상도 (인쇄 300dpi)
- 파일명: `<topic>_<audience>_<YYYY-MM-DD>.pdf`.
- 메타데이터 (제목·작성자·키워드) 설정.
- 보안 (비밀번호·인쇄 차단)은 sensitive deck에만.

## rule (배포)
- 배포 전 검토:
  - 폰트 깨짐 (다른 PC에서 열어보기)
  - 이미지 해상도
  - 링크 동작
  - 슬라이드 번호 일관
  - 마지막 슬라이드 (감사·Q&A)
  - 비밀 정보 (이름·이메일·내부 URL) 마스킹
- 공유 방법:
  - email 첨부 (작은 deck < 10MB)
  - OneDrive/SharePoint link (큰 deck)
  - 사내 공유 폴더
- 발표 후 공유는 PDF + 핵심 메시지 1줄 요약 email.

## rule (인쇄 — 필요 시)
- 핸드아웃 layout (4 또는 6 슬라이드 per page).
- 흑백 인쇄 시 색 의존 콘텐츠 확인 (color.md).
- 인쇄 색공간 CMYK.
- 페이지 번호·헤더 명시.

## rule (presentation 출력)
- 발표용은 16:9 + projector 호환 해상도.
- 노트 view로 발표자 화면 분리 (extended display).
- 발표 mode hotkey 익히기.
- backup: USB + cloud + email 본인 (단일 실패 차단).

## forbidden
- pptx 그대로 외부 공유 (편집 가능 — 권한 의도된 경우만)
- 폰트 깨진 채로 export
- 메타데이터 누락 (작성자·제목 검색·archive 어려움)
- 비밀 정보 노출 (export 전 마스킹 검토)
- 단일 backup (USB만 또는 cloud만)
- 발표 직전 export (사전 dry-run)
- 발표 노트가 슬라이드에 보임 (별 view 사용)
- 비밀번호 protected를 받는 사람에 비밀번호 안 알림
- 메타데이터에 비밀값 (제목·키워드)
- PDF에 form field·hidden text 잔존
