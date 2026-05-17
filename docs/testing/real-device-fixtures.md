# Real Device Fixture Capture

휴대폰에서 직접 실행한 스캔 업로드와 이미지 로컬라이즈 요청을 테스트 fixture 후보로 저장하는 로컬 전용 기능이다.

## Enable

```bash
FIXTURE_CAPTURE_ENABLED=true \
FIXTURE_CAPTURE_ROOT=./test-fixtures/captured \
./gradlew bootRun
```

기본값은 `false`다. prod에서 켜지 않는다.

## Captured Data

### Scan Chunk Upload

Endpoint:

```text
POST /api/v1/floors/{floorId}/scans/chunks
```

Output:

```text
test-fixtures/captured/scan-chunks/{timestamp}-{scanIdParam-or-capture}-{id}/
  request.json
  response.json
  error.json
  upload-{originalFilename}
```

`request.json`에는 `floorId`, `scan_id`, `device_info`, `force`, 파일명, 크기, sha256이 들어간다.
`upload-*`는 휴대폰이 보낸 원본 ZIP이다. ZIP 안의 `rtabmap.db`가 테스트 입력 원본이다.

### Localize Images

Endpoint:

```text
POST /api/slam/v3/localize
```

Output:

```text
test-fixtures/captured/localize/{timestamp}-{buildingId-or-mapId}-{id}/
  request.json
  response.json
  error.json
  images/
    00-{originalFilename}
    01-{originalFilename}
```

`request.json`에는 `building_id`, `map_id`, 이미지 파일명, 크기, sha256이 들어간다.
`response.json`에는 로컬라이즈 성공 결과가 들어간다.
실패하면 `error.json`에 API 오류 코드와 메시지가 들어간다.

## Promotion Rule

`test-fixtures/captured/`는 raw 수집 영역이라 git ignore 대상이다.
테스트로 고정할 데이터만 선별해서 `src/test/resources/fixtures/real-device/`로 옮긴다.

고정 전 확인:

- 개인정보/위치정보 포함 여부
- 이미지가 테스트 목적에 필요한 최소 장면인지
- ZIP 안 `rtabmap.db`가 재현 가능한 스캔인지
- 기대 결과를 사람이 한 번 확인했는지
