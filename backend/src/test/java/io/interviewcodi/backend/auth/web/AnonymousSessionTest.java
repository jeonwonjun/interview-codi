package io.interviewcodi.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.interviewcodi.backend.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 로그인하지 않은 API 요청이 DB에 세션 행을 만들지 않는지 실제 HTTP 서버로 확인한다.
 * 세션 저장(Spring Session 필터)까지 실제와 같게 거치도록 MockMvc 대신 실제 서버를 띄운다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AnonymousSessionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("로그인하지 않은 API 요청은 세션을 만들지 않는다")
    void anonymousApiCallsDoNotCreateSessions() throws Exception {
        int before = countSessions();

        try (HttpClient client = HttpClient.newHttpClient()) {
            for (int i = 0; i < 3; i++) {
                HttpResponse<Void> response = client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me")).build(),
                        HttpResponse.BodyHandlers.discarding());
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("SESSION="));
            }
        }

        assertThat(countSessions()).isEqualTo(before);
    }

    private int countSessions() {
        return jdbcClient.sql("select count(*) from spring_session").query(Integer.class).single();
    }
}
