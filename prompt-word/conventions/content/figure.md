## rule
- figure = 그림·차트·다이어그램·스크린샷·사진.
- 본문 보조 (의미 추가). 장식용 X.
- 캡션: 아래 (`그림 1. 캡션`). 자동 번호.
- 캡션은 stand-alone 이해 가능.
- 출처 인용 (캡션 끝 또는 footer).
- 본문에서 cross-reference로 참조 (`그림 1 참조` — Word `참조 > 상호 참조`).
- 해상도 충분 (인쇄 300 dpi). 화면 전용은 72 dpi 가능.
- alt text 필수 (`figure 우클릭 > 대체 텍스트`).
- 배치는 anchor 명시 (특정 단락 옆 또는 페이지 상·하단).
- text wrapping은 `위·아래` 또는 `한 줄과 같음` 권장. `정사각형` 등 wrap은 신중.
- 크기는 페이지 폭 기준 (full-width 또는 half-width). 임의 X.

## rule (차트)
- 차트는 Excel embed 또는 Word 차트 직접. 이미지 paste X (편집·resolution 손해).
- chart type 선택은 메시지 따라 (`prompt-slide/content/data.md` 동일 원칙):
  - 시간 추이 → line
  - 항목 비교 → bar
  - 비율 → bar > pie
  - 상관관계 → scatter
- 단위·축·legend 명시.
- chartjunk 제거 (3D·gradient·shadow).

## rule (스크린샷)
- 해상도 (Retina: 2x). blur 금지.
- crop은 메시지 영역. 무의미 chrome 제거.
- annotation (화살표·강조 box).
- sensitive 정보 마스킹.
- 같은 문서 안 스크린샷 frame·style 일관.

## rule (다이어그램)
- Word `그리기·SmartArt` 또는 외부 도구 (draw.io·Lucid·Excalidraw) export.
- flow는 좌→우 또는 위→아래 일관.
- node·arrow·색 의미 일관 (legend 필요 시).

## forbidden
- 장식용 figure
- 캡션 누락
- 캡션을 figure 위 (아래가 표준)
- 본문에 "위 그림" / "아래 그림" 모호 (cross-ref 사용)
- alt text 누락 (a11y)
- 저작권 미확인 이미지
- 해상도 부족 (인쇄·확대 시 깨짐)
- sensitive 정보 마스킹 누락
- chart를 이미지 paste (편집 불가)
- 단위·legend 누락
- 같은 문서 figure style 무질서
- chrome (browser bar 등) 무의미 포함
