package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class CaptureFileIo {
    private final ObjectMapper objectMapper;

    CaptureFileIo(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    FixtureCaptureService.CapturedFile copyMultipart(
            org.springframework.web.multipart.MultipartFile file,
            Path targetPrefix
    ) throws IOException {
        Files.createDirectories(targetPrefix.getParent());
        String filename = safeFileName(file.getOriginalFilename());
        Path target = targetPrefix.resolveSibling(targetPrefix.getFileName() + "-" + filename);
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                size += read;
            }
        }
        return new FixtureCaptureService.CapturedFile(
                target.getFileName().toString(),
                file.getOriginalFilename(),
                file.getContentType(),
                size,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String safeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return sanitize(filename);
    }

    String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
