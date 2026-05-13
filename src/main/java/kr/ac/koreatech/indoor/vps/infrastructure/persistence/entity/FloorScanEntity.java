package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "floor_scan")
public class FloorScanEntity {
    @Id
    @Column(name = "floor_scan_id", nullable = false)
    private UUID floorScanId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private FloorEntity floor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private ScanIngestEntity scan;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(nullable = false)
    private String status = "UPLOADED";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "upload_order", nullable = false)
    private int uploadOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FloorScanEntity() {
    }

    public FloorScanEntity(FloorEntity floor, ScanIngestEntity scan, String fileName, Long fileSize, int uploadOrder) {
        this.floor = floor;
        this.scan = scan;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploadOrder = uploadOrder;
    }

    @PrePersist
    void prePersist() {
        if (floorScanId == null) {
            floorScanId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getFloorScanId() {
        return floorScanId;
    }

    public FloorEntity getFloor() {
        return floor;
    }

    public ScanIngestEntity getScan() {
        return scan;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getUploadOrder() {
        return uploadOrder;
    }

    public void setUploadOrder(int uploadOrder) {
        this.uploadOrder = uploadOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
