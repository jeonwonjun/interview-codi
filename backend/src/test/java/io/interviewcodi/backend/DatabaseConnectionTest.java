package io.interviewcodi.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 애플리케이션이 실제 PostgreSQL에 연결되고 Flyway 마이그레이션이 적용되는지 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseConnectionTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("PostgreSQL 18에 연결된다")
    void connectsToPostgres18() {
        Integer serverVersionNum = jdbcClient.sql("select current_setting('server_version_num')::int")
                .query(Integer.class)
                .single();

        assertThat(serverVersionNum).isBetween(180000, 189999);
    }

    @Test
    @DisplayName("적용하지 않은 Flyway 마이그레이션이 없다")
    void flywayHasNoPendingMigrations() {
        assertThat(flyway.info().pending()).isEmpty();
    }
}
