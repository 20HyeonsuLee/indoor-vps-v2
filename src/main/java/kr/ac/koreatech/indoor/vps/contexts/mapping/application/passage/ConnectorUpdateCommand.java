package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.Optional;

public record ConnectorUpdateCommand(
        Optional<String> connectorType,
        Optional<String> connectorKey,
        Optional<String> name,
        Optional<Boolean> mock
) {
}
