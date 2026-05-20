package kr.ac.koreatech.indoor.vps.config;

import kr.ac.koreatech.indoor.vps.contexts.mapping.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationService.Variant;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SlamLocalizer;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Primary/Shadow SLAM localizer Bean 등록. SlamLocalizationService를 같은
 * dependency로 두 instance 만들고 variant만 다르게 줘 동작 분기.
 *
 * <p>Swap 절차: 응답으로 반환할 변종을 바꾸려면 {@link kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationFacade}
 * 의 primary 주입 Qualifier만 교체 (예: "primaryLocalizer" → "shadowLocalizer"). 별도
 * properties toggle 도입 가능하지만 명시적 코드 변경이 의도가 더 명확.
 */
@Configuration
public class SlamLocalizerConfig {
    @Bean
    @Qualifier("primaryLocalizer")
    public SlamLocalizer primaryLocalizer(
            IndoorProperties properties,
            PythonBridge bridge,
            LocalizationMapProvider mapProvider
    ) {
        return new SlamLocalizationService(properties, bridge, mapProvider, Variant.PRIMARY);
    }

    @Bean
    @Qualifier("shadowLocalizer")
    public SlamLocalizer shadowLocalizer(
            IndoorProperties properties,
            PythonBridge bridge,
            LocalizationMapProvider mapProvider
    ) {
        return new SlamLocalizationService(properties, bridge, mapProvider, Variant.SHADOW);
    }
}
