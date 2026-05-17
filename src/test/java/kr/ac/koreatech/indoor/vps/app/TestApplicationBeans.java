package kr.ac.koreatech.indoor.vps.app;

import static org.mockito.Mockito.mock;

import java.util.List;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetGraphUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PlanRouteUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.PassageUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.PoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.FinalizeStreamingScanUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ListScanChunksUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.MergeScansUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ProcessFloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.PushStreamingFramesUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.StartStreamingScanUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.UploadScanChunkUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestApplicationBeans {
    @Bean
    BuildingUseCase buildingUseCase() {
        return mock(BuildingUseCase.class);
    }

    @Bean
    BuildingQueryService buildingQueryService() {
        return mock(BuildingQueryService.class);
    }

    @Bean
    FloorUseCase floorUseCase() {
        return mock(FloorUseCase.class);
    }

    @Bean
    FloorQueryService floorQueryService() {
        return mock(FloorQueryService.class);
    }

    @Bean
    PlanRouteUseCase planRouteUseCase() {
        return mock(PlanRouteUseCase.class);
    }

    @Bean
    GetGraphUseCase getGraphUseCase() {
        return mock(GetGraphUseCase.class);
    }

    @Bean
    GetFloorMapUseCase getFloorMapUseCase() {
        return mock(GetFloorMapUseCase.class);
    }

    @Bean
    PoiUseCase poiUseCase() {
        return mock(PoiUseCase.class);
    }

    @Bean
    PassageUseCase passageUseCase() {
        return mock(PassageUseCase.class);
    }

    @Bean
    SlamLocalizationService slamLocalizationService() {
        return mock(SlamLocalizationService.class);
    }

    @Bean
    StartStreamingScanUseCase startStreamingScanUseCase() {
        return mock(StartStreamingScanUseCase.class);
    }

    @Bean
    PushStreamingFramesUseCase pushStreamingFramesUseCase() {
        return mock(PushStreamingFramesUseCase.class);
    }

    @Bean
    FinalizeStreamingScanUseCase finalizeStreamingScanUseCase() {
        return mock(FinalizeStreamingScanUseCase.class);
    }

    @Bean
    UploadScanChunkUseCase uploadScanChunkUseCase() {
        return mock(UploadScanChunkUseCase.class);
    }

    @Bean
    ListScanChunksUseCase listScanChunksUseCase() {
        return mock(ListScanChunksUseCase.class);
    }

    @Bean
    MergeScansUseCase mergeScansUseCase() {
        return mock(MergeScansUseCase.class);
    }

    @Bean
    ProcessFloorUseCase processFloorUseCase() {
        return mock(ProcessFloorUseCase.class);
    }

    @Bean
    FixtureCaptureService fixtureCaptureService() {
        return mock(FixtureCaptureService.class);
    }

    @Bean
    LocalizationMapProvider localizationMapProvider() {
        return buildingId -> List.of();
    }
}
