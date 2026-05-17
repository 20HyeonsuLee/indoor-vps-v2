package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

public record BuildingCreateCommand(String name, String description, Double latitude, Double longitude) {
}
