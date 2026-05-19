package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Area의 active scan 디렉터리에서 cloud.ply를 찾아 Resource로 반환.
 * 파일이 없으면 404 — BuildJobRunner가 PLY export step을 끝낸 후에만 사용 가능.
 */
@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PointcloudFileResolver {

    static final String CLOUD_FILE_NAME = "cloud.ply";

    private final FloorScanRepository floorScanRepository;
    private final IndoorProperties properties;

    public PointcloudFileResolver(FloorScanRepository floorScanRepository, IndoorProperties properties) {
        this.floorScanRepository = floorScanRepository;
        this.properties = properties;
    }

    public PointcloudResource resolveForArea(UUID areaId) {
        FloorScanEntity floorScan = floorScanRepository
                .findFirstByArea_AreaIdAndActiveTrueOrderByCreatedAtDesc(areaId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "NO_ACTIVE_SCAN", "area has no active scan"));
        Path file = resolveStoragePath(floorScan.getScan().getStoragePath()).resolve(CLOUD_FILE_NAME);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "POINTCLOUD_NOT_AVAILABLE",
                    "pointcloud not exported yet: " + file);
        }
        long size = sizeOrZero(file);
        return new PointcloudResource(new PathResource(file), size);
    }

    public PointcloudResource resolveForFloor(UUID floorId) {
        FloorScanEntity floorScan = floorScanRepository
                .findFirstByFloor_FloorIdAndActiveTrueOrderByCreatedAtDesc(floorId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "NO_ACTIVE_SCAN", "floor has no active scan"));
        Path file = resolveStoragePath(floorScan.getScan().getStoragePath()).resolve(CLOUD_FILE_NAME);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "POINTCLOUD_NOT_AVAILABLE",
                    "pointcloud not exported yet: " + file);
        }
        return new PointcloudResource(new PathResource(file), sizeOrZero(file));
    }

    private Path resolveStoragePath(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        if (fileName != null && "rtabmap.db".equals(fileName.toString())) {
            return path.getParent();
        }
        if (fileName != null && fileName.toString().endsWith(".zip") && path.getParent() != null) {
            return path.getParent();
        }
        return path;
    }

    private long sizeOrZero(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    public record PointcloudResource(Resource resource, long contentLength) {
    }
}
