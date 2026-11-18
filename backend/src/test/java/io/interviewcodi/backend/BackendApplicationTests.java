package io.interviewcodi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 뜬다")
    void contextLoads() {
    }
}
