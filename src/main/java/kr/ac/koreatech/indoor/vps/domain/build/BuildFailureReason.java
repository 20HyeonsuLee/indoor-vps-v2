package kr.ac.koreatech.indoor.vps.domain.build;

public enum BuildFailureReason {
    walkable_coverage_low,
    graph_disconnected,
    model_load_failed,
    intrinsics_missing,
    rtabmap_data_not_ready,
    internal
}
