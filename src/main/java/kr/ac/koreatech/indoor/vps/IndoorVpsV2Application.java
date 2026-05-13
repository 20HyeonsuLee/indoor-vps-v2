package kr.ac.koreatech.indoor.vps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import kr.ac.koreatech.indoor.vps.config.IndoorProperties;

@SpringBootApplication
@EnableConfigurationProperties(IndoorProperties.class)
public class IndoorVpsV2Application {

	public static void main(String[] args) {
		SpringApplication.run(IndoorVpsV2Application.class, args);
	}

}
