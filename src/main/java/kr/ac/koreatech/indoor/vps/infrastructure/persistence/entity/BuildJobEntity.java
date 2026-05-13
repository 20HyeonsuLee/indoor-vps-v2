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
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.domain.build.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.domain.build.BuildStep;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> counts;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "worker_id")
    private String workerId;

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

    public void markRunning(String workerId, Instant now) {
        this.state = BuildState.running;
        this.currentStep = BuildStep.init;
        this.progress = 0.05;
        this.startedAt = now;
        this.finishedAt = null;
        this.lockedAt = now;
        this.workerId = workerId;
        this.attemptCount++;
        this.failureReason = null;
        this.failureDetail = null;
        this.counts = null;
    }

    public void markPersisting() {
        this.currentStep = BuildStep.persist;
        this.progress = 0.95;
    }

    public void markSucceeded(Map<String, Object> counts) {
        this.state = BuildState.succeeded;
        this.currentStep = BuildStep.done;
        this.progress = 1.0;
        this.finishedAt = Instant.now();
        this.counts = counts;
        this.failureReason = null;
        this.failureDetail = null;
        clearLock();
    }

    public void markFailed(BuildFailureReason failureReason, String failureDetail) {
        this.state = BuildState.failed;
        this.currentStep = BuildStep.done;
        this.progress = 1.0;
        this.finishedAt = Instant.now();
        this.failureReason = failureReason;
        this.failureDetail = failureDetail == null ? null : failureDetail.substring(0, Math.min(failureDetail.length(), 2000));
        clearLock();
    }

    private void clearLock() {
        this.lockedAt = null;
        this.workerId = null;
    }
}
