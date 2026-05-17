package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository.PassageRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PassageUseCase {
    private final BuildingQueryService buildingQuery;
    private final VerticalConnectorRepository connectorRepository;

    public PassageUseCase(BuildingQueryService buildingQuery, VerticalConnectorRepository connectorRepository) {
        this.buildingQuery = buildingQuery;
        this.connectorRepository = connectorRepository;
    }

    public List<VerticalPassageResult.Summary> listPassages(UUID buildingId) {
        buildingQuery.requireBuilding(buildingId);
        Map<UUID, PassageAccumulator> passages = new LinkedHashMap<>();
        for (PassageRow row : connectorRepository.findPassageRowsByBuildingId(buildingId)) {
            PassageAccumulator passage = passages.computeIfAbsent(
                    row.getPassageId(),
                    ignored -> new PassageAccumulator(row)
            );
            if (row.getStopId() != null) {
                passage.segments.add(new VerticalPassageResult.Segment(
                        row.getStopId().toString(),
                        row.getLevelId(),
                        row.getRouteNodeId() == null ? null : row.getRouteNodeId().toString(),
                        row.getX(),
                        row.getY(),
                        row.getFloorId() == null ? null : row.getFloorId().toString(),
                        row.getConnectorType()
                ));
            }
        }
        return passages.values().stream()
                .map(PassageAccumulator::toResult)
                .toList();
    }

    private static final class PassageAccumulator {
        private final PassageRow row;
        private final List<VerticalPassageResult.Segment> segments = new ArrayList<>();

        private PassageAccumulator(PassageRow row) {
            this.row = row;
        }

        private VerticalPassageResult.Summary toResult() {
            return new VerticalPassageResult.Summary(
                    row.getPassageId(),
                    row.getBuildingId(),
                    row.getConnectorType(),
                    row.getConnectorKey(),
                    row.getName(),
                    row.isMock(),
                    List.copyOf(segments)
            );
        }
    }
}
