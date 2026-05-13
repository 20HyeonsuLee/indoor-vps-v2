package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

public final class DbEnums {
    private DbEnums() {
    }

    public enum BuildState {
        not_started,
        pending,
        running,
        succeeded,
        failed,
        cancelled
    }

    public enum BuildStep {
        init,
        floor_seg,
        back_project,
        walkable_grid,
        skeleton,
        node_placement,
        poi_projection,
        quality_gate,
        persist,
        done
    }

    public enum BuildFailureReason {
        walkable_coverage_low,
        graph_disconnected,
        model_load_failed,
        intrinsics_missing,
        rtabmap_data_not_ready,
        internal
    }

    public enum NodeType {
        junction,
        endpoint,
        corridor,
        poi,
        poi_attach
    }

    public enum EdgeType {
        skeleton,
        poi_spur
    }
}
