package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.List;
import java.util.UUID;

public record MergeBuildCommand(
        UUID floorId,
        List<String> scanPaths
) {
}
