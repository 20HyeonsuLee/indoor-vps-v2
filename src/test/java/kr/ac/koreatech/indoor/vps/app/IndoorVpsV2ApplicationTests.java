package kr.ac.koreatech.indoor.vps.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestApplicationBeans.class)
class IndoorVpsV2ApplicationTests {

	@Test
	void contextLoads() {
	}

}
