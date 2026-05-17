package kr.ac.koreatech.indoor.vps.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import kr.ac.koreatech.indoor.vps.config.IndoorProperties;

@SpringBootApplication(scanBasePackages = "kr.ac.koreatech.indoor.vps")
@EnableConfigurationProperties(IndoorProperties.class)
@EnableScheduling
public class IndoorVpsV2Application {

	public static void main(String[] args) {
		SpringApplication.run(IndoorVpsV2Application.class, args);
	}

}
