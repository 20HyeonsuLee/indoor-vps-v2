package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

public record ConnectorCreateCommand(
        String connectorType,
        String connectorKey,
        String name
) {
}
