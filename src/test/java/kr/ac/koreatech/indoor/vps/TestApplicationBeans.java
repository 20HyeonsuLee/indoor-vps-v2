package kr.ac.koreatech.indoor.vps;

import static org.mockito.Mockito.mock;

import java.util.List;
import kr.ac.koreatech.indoor.vps.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.application.building.BuildingApplicationService;
import kr.ac.koreatech.indoor.vps.application.floor.FloorApplicationService;
import kr.ac.koreatech.indoor.vps.application.navigation.NavigationApplicationService;
import kr.ac.koreatech.indoor.vps.application.poi.PoiApplicationService;
import kr.ac.koreatech.indoor.vps.application.scan.ScanApplicationService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestApplicationBeans {
    @Bean
    BuildingApplicationService buildingApplicationService() {
        return mock(BuildingApplicationService.class);
    }

    @Bean
    FloorApplicationService floorApplicationService() {
        return mock(FloorApplicationService.class);
    }

    @Bean
    NavigationApplicationService navigationApplicationService() {
        return mock(NavigationApplicationService.class);
    }

    @Bean
    PoiApplicationService poiApplicationService() {
        return mock(PoiApplicationService.class);
    }

    @Bean
    ScanApplicationService scanApplicationService() {
        return mock(ScanApplicationService.class);
    }

    @Bean
    LocalizationMapProvider localizationMapProvider() {
        return buildingId -> List.of();
    }
}
