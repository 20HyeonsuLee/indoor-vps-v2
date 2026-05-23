## rule
- 16:9 widescreen (1920x1080) 기준.
- safe area: 가장자리 5~8% margin. 빔 프로젝터 잘림 방지.
- **grid** 사용 (12-column 또는 8-column). 모든 element가 grid에 align.
- 정렬: 좌·우·중앙 명시. 0.5px 차이 없게.
- 여백은 의도적. element 간 일관 gap (8/16/24/32 px 등 단계화).
- 시선 흐름: 좌→우, 위→아래. F-pattern 또는 Z-pattern 인지.
- 균형: symmetric 또는 의도적 asymmetric. random 위치 X.
- 표준 layout 패턴:
  - **title-only**: 표지·section divider
  - **headline + body** (단일 column): 메시지 위주
  - **headline + 2 column**: 비교 (vs)
  - **headline + 3 column**: trio (예: 3 단계)
  - **headline + full-bleed visual**: 큰 시각
  - **headline + chart**: 데이터 슬라이드
- master slide로 layout 표준화. 매번 직접 그리지 X.

## forbidden
- element가 grid 밖 (눈에 거슬림)
- 정렬 0.5px 차이 (의식적 정렬 또는 snap)
- safe area 침범 (가장자리 잘림 위험)
- column 4개 이상 (가독성 ↓)
- 같은 deck에 layout 패턴 무질서 (5+ 다른 layout)
- 빈 공간 무계획 (의도 vs 게으름 차이)
- 시선 흐름 역행 (오른쪽에서 시작)
- master 사용 안 하고 매번 직접 그림 (일관성 깨짐)
- 한 슬라이드 안 element 간 gap 무질서
- 가장 중요한 element가 가장자리에
