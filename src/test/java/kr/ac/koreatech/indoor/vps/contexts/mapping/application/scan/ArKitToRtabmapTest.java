package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import org.junit.jupiter.api.Test;

class ArKitToRtabmapTest {

    @Test
    void convertsZeroVectorToZero() {
        Point3 result = ArKitToRtabmap.convert(0.0, 0.0, 0.0);

        assertThat(result.x()).isCloseTo(0.0, within(1e-9));
        assertThat(result.y()).isCloseTo(0.0, within(1e-9));
        assertThat(result.z()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void appliesAxisMapping() {
        // ARKit (tx=0, ty=1, tz=0) → rt_x=-tz=0, rt_y=-tx=0, rt_z=ty=1
        Point3 result = ArKitToRtabmap.convert(0.0, 1.0, 0.0);

        assertThat(result.x()).isCloseTo(0.0, within(1e-9));
        assertThat(result.y()).isCloseTo(0.0, within(1e-9));
        assertThat(result.z()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void convertsKnownPoint() {
        // ARKit (tx=1, ty=2, tz=3) → rt_x=-3, rt_y=-1, rt_z=2
        Point3 result = ArKitToRtabmap.convert(1.0, 2.0, 3.0);

        assertThat(result.x()).isCloseTo(-3.0, within(1e-9));
        assertThat(result.y()).isCloseTo(-1.0, within(1e-9));
        assertThat(result.z()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void convertsNegativeCoordinates() {
        Point3 result = ArKitToRtabmap.convert(-1.0, -2.0, -3.0);

        assertThat(result.x()).isCloseTo(3.0, within(1e-9));
        assertThat(result.y()).isCloseTo(1.0, within(1e-9));
        assertThat(result.z()).isCloseTo(-2.0, within(1e-9));
    }
}
