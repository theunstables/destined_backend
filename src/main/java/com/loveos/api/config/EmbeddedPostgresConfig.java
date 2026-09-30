package com.loveos.api.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Runs a real PostgreSQL server as a child process of this JVM.
 *
 * <p>WHY THIS EXISTS. PostgreSQL is installed on this machine, but no Windows
 * service was registered for it and the superuser password is unknown. Creating
 * a service, editing {@code pg_hba.conf} or resetting that password all require
 * administrator rights that are not available here. Rather than weaken the
 * machine's configuration to work around that, this starts the genuine
 * PostgreSQL binaries under the current user account: same engine, same SQL,
 * same wire protocol, but owned entirely by this project.
 *
 * <p>The data directory is deliberate and persistent. Restarting the application
 * keeps every row, which is the whole reason for moving off H2 — a database that
 * forgets is not exercising the code that matters.
 *
 * <p>DEVELOPMENT ONLY, and guarded structurally rather than by configuration.
 * The class is annotated {@code @Profile("pgdev")} and the dependencies it needs
 * are excluded from the packaged jar, so a deployed application cannot start a
 * database of its own no matter how it is configured. That matters: a service
 * that silently provisions its own storage will happily run for weeks against
 * the wrong one.
 */
@Configuration
@Profile("pgdev")
public class EmbeddedPostgresConfig {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);

  /** Port 5433, to stay clear of any system PostgreSQL that may appear on 5432. */
  private static final int PORT = 5433;

  /**
   * Starts the server and hands back its {@link DataSource}.
   *
   * <p>Returning the DataSource from the same bean that owns the server is what
   * guarantees ordering: Spring cannot hand a connection pool to Hibernate
   * before this method has returned, and this method does not return until
   * PostgreSQL is accepting connections. Declaring the two separately would
   * invite a race that shows up only on slower machines.
   */
  @Bean
  public DataSource dataSource() throws IOException {
    Path dataDirectory = dataDirectory();
    Files.createDirectories(dataDirectory.getParent());

    log.info("starting embedded PostgreSQL on port {} using {}", PORT, dataDirectory);

    EmbeddedPostgres postgres = EmbeddedPostgres.builder()
        .setPort(PORT)
        .setDataDirectory(dataDirectory)
        // false means "keep what is already there" — without it the directory is
        // wiped on every boot and this is no better than an in-memory database.
        .setCleanDataDirectory(false)
        .start();

    log.info("embedded PostgreSQL ready: {}", postgres.getJdbcUrl("postgres", "postgres"));

    return DataSourceBuilder.create()
        .driverClassName("org.postgresql.Driver")
        .url(postgres.getJdbcUrl("postgres", "postgres"))
        .username("postgres")
        .build();
  }

  /**
   * Kept out of the source tree, under {@code %LOCALAPPDATA%}.
   *
   * <p>Two reasons. The project lives in a OneDrive-synced folder, and a sync
   * client copying database files while PostgreSQL is writing them is a good way
   * to produce corruption. And {@code LOCALAPPDATA} is per-machine rather than
   * roaming, which is what a database wants.
   */
  private static Path dataDirectory() {
    String localAppData = System.getenv("LOCALAPPDATA");
    Path base = localAppData != null && !localAppData.isBlank()
        ? Paths.get(localAppData)
        : Paths.get(System.getProperty("user.home"), ".local", "share");

    return base.resolve("loveos").resolve("pgdata");
  }
}
