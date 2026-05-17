package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

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
class ScanFileIo {

    StoredFile copy(org.springframework.web.multipart.MultipartFile file, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                size += read;
            }
        }
        return new StoredFile(size, HexFormat.of().formatHex(digest.digest()));
    }

    StoredFile hashFile(Path path) throws IOException {
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        return new StoredFile(size, HexFormat.of().formatHex(digest.digest()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record StoredFile(long size, String sha256) {
    }
}
