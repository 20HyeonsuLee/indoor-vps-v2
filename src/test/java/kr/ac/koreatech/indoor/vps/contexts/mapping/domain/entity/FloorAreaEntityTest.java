package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorAreaEntityTest {

    @Test
    void createDefault_setsIsDefaultTrueAndLabel() {
        FloorEntity floor = mockFloor();
        FloorAreaEntity area = FloorAreaEntity.createDefault(floor);

        assertThat(area.isDefault()).isTrue();
        assertThat(area.getLabel()).isEqualTo("Area 1");
        assertThat(area.getAreaIndex()).isEqualTo(0);
        assertThat(area.getFloor()).isSameAs(floor);
    }

    @Test
    void create_setsIsDefaultFalse() {
        FloorEntity floor = mockFloor();
        FloorAreaEntity area = FloorAreaEntity.create(floor, 1, "Lab Wing");

        assertThat(area.isDefault()).isFalse();
        assertThat(area.getLabel()).isEqualTo("Lab Wing");
        assertThat(area.getAreaIndex()).isEqualTo(1);
    }

    @Test
    void create_usesAutoLabelWhenNull() {
        FloorEntity floor = mockFloor();
        FloorAreaEntity area = FloorAreaEntity.create(floor, 2, null);

        assertThat(area.getLabel()).isEqualTo("Area 3");
    }

    @Test
    void rename_updatesLabel() {
        FloorEntity floor = mockFloor();
        FloorAreaEntity area = FloorAreaEntity.createDefault(floor);
        area.rename("Main Hall");

        assertThat(area.getLabel()).isEqualTo("Main Hall");
    }

    @Test
    void rename_ignoresBlank() {
        FloorEntity floor = mockFloor();
        FloorAreaEntity area = FloorAreaEntity.createDefault(floor);
        area.rename("   ");

        assertThat(area.getLabel()).isEqualTo("Area 1");
    }

    private FloorEntity mockFloor() {
        FloorEntity floor = mock(FloorEntity.class);
        when(floor.getFloorId()).thenReturn(UUID.randomUUID());
        return floor;
    }
}
