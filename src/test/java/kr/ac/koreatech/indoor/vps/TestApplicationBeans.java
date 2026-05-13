package kr.ac.koreatech.indoor.vps;

import java.lang.reflect.Proxy;
import java.util.List;
import kr.ac.koreatech.indoor.vps.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.application.VpsService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestApplicationBeans {
    @Bean
    VpsService vpsService() {
        return (VpsService) Proxy.newProxyInstance(
                VpsService.class.getClassLoader(),
                new Class<?>[] {VpsService.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "test VpsService proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    throw new UnsupportedOperationException("VpsService test proxy does not execute API methods");
                }
        );
    }

    @Bean
    LocalizationMapProvider localizationMapProvider() {
        return buildingId -> List.of();
    }
}
