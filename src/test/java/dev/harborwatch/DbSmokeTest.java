package dev.harborwatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DbSmokeTest {
    
    @Test
    void migrationsApplyAndTablesExist() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine")) {
            pg.start();
            Flyway.configure()
                  .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                  .locations("classpath:db/migration")
                  .load().migrate();

            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 ResultSet rs = c.createStatement().executeQuery("SELECT to_regclass('public.performance_data')")) {
                rs.next();
                assertTrue(rs.getString(1) != null);
            }
        }
    }
}
