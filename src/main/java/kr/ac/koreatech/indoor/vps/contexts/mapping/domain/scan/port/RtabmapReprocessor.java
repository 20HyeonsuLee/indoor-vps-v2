package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RtabmapReprocessor {

    RtabmapReprocessResult reprocess(UUID scanId, Path inputDb);

    record RtabmapReprocessResult(
            String status,
            String reason,
            Path inputDbPath,
            Path outputDbPath,
            Path binaryPath,
            List<String> command,
            Duration duration,
            Integer exitCode,
            String stdoutTail,
            String stderrTail
    ) {
        public static RtabmapReprocessResult skipped(String reason, Path inputDb, Path outputDb, Map<String, Object> detail) {
            return new RtabmapReprocessResult(
                    "skipped",
                    reason,
                    inputDb,
                    outputDb,
                    null,
                    List.of(),
                    Duration.ZERO,
                    null,
                    "",
                    detail == null ? "" : detail.toString()
            );
        }

        public static RtabmapReprocessResult alreadyReprocessed(Path inputDb, Path outputDb, Path binary) {
            return new RtabmapReprocessResult(
                    "succeeded",
                    "already_reprocessed",
                    inputDb,
                    outputDb,
                    binary,
                    List.of(),
                    Duration.ZERO,
                    0,
                    "",
                    ""
            );
        }

        public static RtabmapReprocessResult succeeded(
                Path inputDb,
                Path outputDb,
                Path binary,
                List<String> command,
                Duration duration,
                String stdoutTail,
                String stderrTail
        ) {
            return new RtabmapReprocessResult(
                    "succeeded",
                    "completed",
                    inputDb,
                    outputDb,
                    binary,
                    List.copyOf(command),
                    duration,
                    0,
                    stdoutTail,
                    stderrTail
            );
        }

        public static RtabmapReprocessResult failed(
                String reason,
                Path inputDb,
                Path outputDb,
                Path binary,
                List<String> command,
                Duration duration,
                Integer exitCode,
                String stdoutTail,
                String stderrTail
        ) {
            return new RtabmapReprocessResult(
                    "failed",
                    reason,
                    inputDb,
                    outputDb,
                    binary,
                    List.copyOf(command),
                    duration,
                    exitCode,
                    stdoutTail,
                    stderrTail
            );
        }

        public boolean hasUsableOutput() {
            return "succeeded".equals(status) && outputDbPath != null && Files.exists(outputDbPath);
        }

        public Map<String, Object> metadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("status", status);
            metadata.put("reason", reason);
            metadata.put("input_db_path", inputDbPath == null ? null : inputDbPath.toString());
            metadata.put("output_db_path", outputDbPath == null ? null : outputDbPath.toString());
            metadata.put("binary_path", binaryPath == null ? null : binaryPath.toString());
            metadata.put("duration_ms", duration == null ? 0 : duration.toMillis());
            metadata.put("exit_code", exitCode);
            metadata.put("command", new ArrayList<>(command));
            metadata.put("stdout_tail", stdoutTail == null ? "" : stdoutTail);
            metadata.put("stderr_tail", stderrTail == null ? "" : stderrTail);
            return metadata;
        }

        public Path effectiveDbPath() {
            return hasUsableOutput() ? outputDbPath : inputDbPath;
        }
    }

    final class RtabmapReprocessException extends RuntimeException {
        private final RtabmapReprocessResult result;

        public RtabmapReprocessException(String message, RtabmapReprocessResult result) {
            super(message);
            this.result = result;
        }

        public RtabmapReprocessResult result() {
            return result;
        }
    }
}
