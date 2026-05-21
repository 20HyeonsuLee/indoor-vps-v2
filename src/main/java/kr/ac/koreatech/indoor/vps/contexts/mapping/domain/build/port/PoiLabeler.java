package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port;

import java.util.UUID;

/**
 * 빌드 직후 호출되어 해당 scan 의 poi_canonical 라벨/이름/카테고리를
 * AI 비전 분석으로 채우는 외부 시스템 포트.
 *
 * <p>구현체는 fire-and-forget 으로 동작해야 한다 — 호출자(BuildJobRunner)는
 * 라벨링 완료를 기다리지 않고 즉시 다음 작업으로 진행한다.
 */
public interface PoiLabeler {
    /**
     * 주어진 scan 의 미처리 POI 들을 백그라운드에서 라벨링한다.
     * 실패해도 빌드는 이미 succeeded 상태이므로 예외를 던지지 않는다.
     */
    void labelScan(UUID scanId);
}
