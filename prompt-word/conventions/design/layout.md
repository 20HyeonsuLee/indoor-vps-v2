## rule (페이지)
- 종이 크기:
  - **A4** (한국·국제 표준)
  - **Letter** (북미)
- 방향: 세로 default. 큰 표·landscape는 해당 페이지만.
- 여백:
  - 상·하: 2.5~3 cm
  - 좌·우: 2.5 cm
  - 제본 여백 (인쇄 제본 시): 0.5~1 cm 추가
- 페이지 번호:
  - 위치: 하단 가운데 또는 우측
  - 시작: 표지 제외, 본문 1부터 (목차는 i, ii 로마 숫자 가능)
- 머리말 (header): 문서 제목 또는 장 이름 + 작성자/날짜 (옵션).
- 꼬리말 (footer): 페이지 번호 + 회사 로고/이름.
- 짝·홀수 페이지 다른 머리말 가능 (양면 인쇄).

## rule (섹션)
- Word `레이아웃 > 구역 나누기` (section break).
- 사용 case:
  - 표지·목차·본문 머리말 다름
  - landscape 페이지 끼움
  - 다단 시작·끝
- 섹션 break 명시 (보이는 형식 기호로 확인).

## rule (단)
- 본문은 1단 default.
- 학술·뉴스레터는 2단 (`레이아웃 > 단`).
- 다단 시 column width·spacing 일관.

## rule (페이지 break)
- 새 장 (제목 1)은 새 페이지 시작 (`스타일 > 단락 앞 페이지 나누기`).
- 빈 줄 여러 개로 새 페이지 X (page break 사용).
- widow/orphan 제어 (`단락 > 줄 및 페이지 나누기`):
  - heading 다음 본문 1줄만 페이지 끝 X
  - 단락 마지막 1줄만 다음 페이지 X

## rule (목차·index)
- 목차는 자동 (`참조 > 목차`).
- index는 색인 항목 표시 후 자동 생성 (긴 매뉴얼).
- 목차 page 번호와 본문 일치 확인 (final 직전 update).

## forbidden
- 직접 여백 변경 (style·page setup으로)
- 페이지 번호 누락 (memo·1 page 외)
- 빈 줄로 새 페이지 (page break 사용)
- 머리말·꼬리말 무질서
- 섹션 break 없이 layout 변경 시도
- widow/orphan 무시
- 수동 목차
- landscape 페이지를 전체 문서로
- 페이지 번호 표지에 (보통 미표시)
- 머리말에 sensitive 정보
- A4와 Letter 혼용 (한 문서 통일)
- 인쇄 제본 여백 누락 (제본 시 글자 잘림)
