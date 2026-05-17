package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

public record BuildingUpdateCommand(String name, String description, Double latitude, Double longitude) {
}
