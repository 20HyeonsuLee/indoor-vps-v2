package kr.ac.koreatech.indoor.vps.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 개발용 CORS. Vite dev 서버(그래프 에디터 등)가 localhost:8080 API를 호출할 수 있게 허용.
 * local profile 한정 — prod origin은 별도 게이트웨이/설정에서 관리.
 */
@Configuration
@Profile("local")
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("ETag")
                .maxAge(3600);
    }
}
