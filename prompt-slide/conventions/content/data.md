## rule (chart 선택)
- 메시지에 맞는 chart 선택 (Tufte/Cole Nussbaumer 원칙):
  - **시간 추이**: line chart
  - **항목 비교**: bar chart (가로 권장, 라벨 가독)
  - **비율**: bar (≤ 5 항목) > pie (≤ 3 항목, 그 외 X)
  - **상관관계**: scatter
  - **분포**: histogram·box plot
  - **순위**: 정렬된 bar
  - **단일 큰 숫자**: big number + 단위 + context (전년 대비 등)
- 슬라이드 1개 = chart 1개 권장. 비교가 필요하면 small multiples.

## rule (시각 강조)
- 메시지 관련 데이터는 **강조 색**, 나머지는 **회색**으로.
- chart title은 결론 (headline.md와 동일 원칙).
- legend·축 라벨·data label은 필요한 것만. tick·grid·spine 등 chartjunk 제거.
- 단위 명시 (%·원·ms). 축 0 시작이 default — break는 misleading.
- y-axis range를 가지치지 X (왜곡).
- 데이터 출처 명시 (footnote 작은 글씨).

## rule (표)
- 표는 비교가 다차원일 때만 (1차원은 차트).
- 행·열 모두 의미 있는 라벨.
- 강조 cell은 bold·색.
- 정렬: 첫 column 기준 또는 의미 있는 순서.
- 표 안 숫자 정렬 (오른쪽).
- 빈 cell은 `—` 또는 `N/A`. 빈 칸 X.
- 행 20개·열 8개 초과면 슬라이드 분리 또는 별도 자료.

## rule (인포그래픽·다이어그램)
- flow는 좌→우 또는 위→아래 일관.
- node는 같은 모양/크기 (의미 다를 때만 변형).
- arrow는 의미 명확 (실선·점선 의미 정의).
- 색 의미는 일관 (예: 녹색=ok, 빨강=fail).

## forbidden
- chartjunk (3D bar·gradient fill·shadow 남용)
- pie chart 5조각 이상
- 데이터 출처 누락
- y-axis 0 안 시작 (특히 bar — 왜곡)
- 모든 데이터에 같은 강조 (signal-to-noise 깨짐)
- 단위 누락
- legend 위치 무질서 (위·옆 일관)
- 슬라이드 1장에 chart 3개 이상
- 표 안 cell 빈 채로
- 무지개 색 (rainbow palette) 사용
- chart title이 단순 데이터 명 (결론 X)
- 작은 글씨로 axis label (≥ 12pt)
- table cell 너무 빽빽 (여백 부족)
