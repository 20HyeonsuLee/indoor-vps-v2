package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildState;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildStep;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "build_job")
public class BuildJobEntity {
    @Id
    @Column(name = "build_job_id", nullable = false)
    private UUID buildJobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private ScanIngestEntity scan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "build_state")
    private BuildState state = BuildState.pending;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "current_step", columnDefinition = "build_step")
    private BuildStep currentStep;

    private Double progress = 0.0;

    @Column(name = "enqueued_at", nullable = false)
    private Instant enqueuedAt;

    @Column(name = "failure_reason", columnDefinition = "build_failure_reason")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private BuildFailureReason failureReason;

    @Column(name = "failure_detail")
    private String failureDetail;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected BuildJobEntity() {
    }

    public BuildJobEntity(ScanIngestEntity scan) {
        this.scan = scan;
    }

    @PrePersist
    void prePersist() {
        if (buildJobId == null) {
            buildJobId = UUID.randomUUID();
        }
        if (enqueuedAt == null) {
            enqueuedAt = Instant.now();
        }
    }

    public UUID getBuildJobId() {
        return buildJobId;
    }

    public ScanIngestEntity getScan() {
        return scan;
    }

    public BuildState getState() {
        return state;
    }

    public Double getProgress() {
        return progress;
    }

    public BuildFailureReason getFailureReason() {
        return failureReason;
    }

    public String getFailureDetail() {
        return failureDetail;
    }
}
