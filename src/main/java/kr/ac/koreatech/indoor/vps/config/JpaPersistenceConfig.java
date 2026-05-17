package kr.ac.koreatech.indoor.vps.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
@EntityScan("kr.ac.koreatech.indoor.vps.contexts")
@EnableJpaRepositories("kr.ac.koreatech.indoor.vps.contexts")
public class JpaPersistenceConfig {
}
