package kr.ac.koreatech.indoor.vps.app;

import static org.mockito.Mockito.mock;

import java.util.List;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.NavigationApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.PassageApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.PoiApplicationService;
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
    PassageApplicationService passageApplicationService() {
        return mock(PassageApplicationService.class);
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
