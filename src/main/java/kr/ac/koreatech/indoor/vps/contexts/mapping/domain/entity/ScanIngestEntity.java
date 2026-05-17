package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scan_ingest")
public class ScanIngestEntity {
    @Id
    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "payload_sha256", nullable = false)
    private String payloadSha256;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_info", columnDefinition = "jsonb")
    private Map<String, Object> deviceInfo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "build_state", nullable = false, columnDefinition = "build_state")
    private BuildState buildState = BuildState.not_started;

    @Column(name = "build_job_id")
    private UUID buildJobId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    protected ScanIngestEntity() {
    }

    public ScanIngestEntity(UUID scanId, String payloadSha256, String storagePath, Map<String, Object> deviceInfo, UUID areaId) {
        this.scanId = scanId;
        this.payloadSha256 = payloadSha256;
        this.storagePath = storagePath;
        this.deviceInfo = deviceInfo;
        this.areaId = areaId;
    }

    @PrePersist
    void prePersist() {
        if (ingestedAt == null) {
            ingestedAt = Instant.now();
        }
    }

    public UUID getScanId() {
        return scanId;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public void replacePayload(String payloadSha256, String storagePath, Map<String, Object> deviceInfo) {
        this.payloadSha256 = payloadSha256;
        this.storagePath = storagePath;
        this.deviceInfo = deviceInfo;
        this.ingestedAt = Instant.now();
        this.replacedAt = Instant.now();
        this.buildState = BuildState.not_started;
        this.buildJobId = null;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public BuildState getBuildState() {
        return buildState;
    }

    public void changeBuildState(BuildState buildState) {
        this.buildState = buildState;
    }

    public UUID getBuildJobId() {
        return buildJobId;
    }

    public void attachBuildJob(UUID buildJobId) {
        this.buildJobId = buildJobId;
    }

    public Map<String, Object> getDeviceInfo() {
        return deviceInfo;
    }

    public void mergeDeviceInfo(Map<String, Object> additional) {
        if (additional == null || additional.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>();
        if (this.deviceInfo != null) {
            merged.putAll(this.deviceInfo);
        }
        merged.putAll(additional);
        this.deviceInfo = merged;
    }
}
