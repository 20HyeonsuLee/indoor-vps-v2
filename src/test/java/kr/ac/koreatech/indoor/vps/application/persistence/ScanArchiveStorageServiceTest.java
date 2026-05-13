package kr.ac.koreatech.indoor.vps.application.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ScanArchiveStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesZipArchiveUnderCanonicalScanRoot() throws Exception {
        UUID scanId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ScanArchiveStorageService service = service(tempDir);
        byte[] archive = zip(scanId.toString().toUpperCase(), false);

        ScanArchiveStorageService.StoredScanArchive stored = service.store(
                scanId,
                new MockMultipartFile("file", "scan.zip", "application/zip", archive),
                false
        );

        Path scanRoot = tempDir.resolve("scans").resolve(scanId.toString());
        assertThat(stored.storagePath()).isEqualTo("scans/" + scanId);
        assertThat(stored.fileName()).isEqualTo("scan.zip");
        assertThat(stored.size()).isEqualTo(archive.length);
        assertThat(Files.readString(scanRoot.resolve("rtabmap.db"))).isEqualTo("rtabmap");
        assertThat(Files.readString(scanRoot.resolve("scan_metadata.db"))).isEqualTo("sidecar");
    }

    @Test
    void resolvesScanIdFromZipRootWhenRequestOmitsScanId() throws Exception {
        UUID scanId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ScanArchiveStorageService service = service(tempDir);

        UUID resolved = service.resolveScanId(
                new MockMultipartFile("file", "scan.zip", "application/zip", zip(scanId.toString(), false)),
                null
        );

        assertThat(resolved).isEqualTo(scanId);
    }

    @Test
    void forceReplacementKeepsExistingScanWhenNewArchiveIsInvalid() throws Exception {
        UUID scanId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Path scanRoot = tempDir.resolve("scans").resolve(scanId.toString());
        Files.createDirectories(scanRoot);
        Files.writeString(scanRoot.resolve("rtabmap.db"), "old-rtabmap");
        Files.writeString(scanRoot.resolve("scan_metadata.db"), "old-sidecar");
        ScanArchiveStorageService service = service(tempDir);

        assertThatThrownBy(() -> service.store(
                scanId,
                new MockMultipartFile("file", "scan.zip", "application/zip", zipMissingRequired(scanId.toString())),
                true
        ))
                .isInstanceOf(ClientApiException.class);

        assertThat(Files.readString(scanRoot.resolve("rtabmap.db"))).isEqualTo("old-rtabmap");
        assertThat(Files.readString(scanRoot.resolve("scan_metadata.db"))).isEqualTo("old-sidecar");
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        UUID scanId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ScanArchiveStorageService service = service(tempDir);

        assertThatThrownBy(() -> service.store(
                scanId,
                new MockMultipartFile("file", "scan.zip", "application/zip", zip(scanId.toString(), true)),
                false
        ))
                .isInstanceOfSatisfying(ClientApiException.class, error ->
                        assertThat(error.code()).isEqualTo("SCAN_ARCHIVE_INVALID"));
    }

    private ScanArchiveStorageService service(Path storageRoot) {
        IndoorProperties properties = new IndoorProperties();
        properties.setStorageRoot(storageRoot);
        return new ScanArchiveStorageService(properties);
    }

    private byte[] zip(String root, boolean withZipSlip) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, root + "/rtabmap.db", "rtabmap");
            entry(zip, root + "/scan_metadata.db", "sidecar");
            if (withZipSlip) {
                entry(zip, root + "/../evil.txt", "evil");
            }
        }
        return bytes.toByteArray();
    }

    private byte[] zipMissingRequired(String root) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, root + "/scan_metadata.db", "sidecar");
        }
        return bytes.toByteArray();
    }

    private void entry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes());
        zip.closeEntry();
    }
}
