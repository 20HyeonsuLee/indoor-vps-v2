package kr.ac.koreatech.indoor.vps.karate.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileTreeCleaner {
    private FileTreeCleaner() {
    }

    public static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
