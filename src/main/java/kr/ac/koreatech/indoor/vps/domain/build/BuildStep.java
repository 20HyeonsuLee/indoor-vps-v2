package kr.ac.koreatech.indoor.vps.domain.build;

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
