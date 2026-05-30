package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.List;

public record PolygonCommand(List<Vertex> exterior) {
    public record Vertex(double x, double y, double z) {
    }
}
