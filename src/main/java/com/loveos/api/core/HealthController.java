package com.loveos.api.core;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe.
 *
 * <p>DELIBERATELY SHALLOW — it does not touch the database. A health check that
 * fails when a dependency is down causes the orchestrator to kill and restart
 * every instance during a database blip, turning a recoverable outage into a
 * restart storm that prevents recovery. This answers only "is this process able
 * to serve HTTP", which is the question a load balancer is actually asking.
 *
 * <p>It returns nothing about versions, hostnames or configuration: it is
 * unauthenticated, so anything it reveals is revealed to everyone.
 */
@RestController
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  private final DataSource dataSource;

  public HealthController(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  /**
   * Readiness probe. A database outage removes this instance from traffic but
   * does not ask the orchestrator to restart it, avoiding a restart storm.
   */
  @GetMapping("/health/ready")
  public ResponseEntity<Map<String, String>> readiness() {
    try (Connection connection = dataSource.getConnection()) {
      if (!connection.isValid(2)) {
        return unavailable();
      }
      return ResponseEntity.ok(Map.of("status", "ok", "database", "ready"));
    } catch (Exception unavailable) {
      log.warn("readiness check failed: {}", unavailable.getMessage());
      return unavailable();
    }
  }

  private static ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", "unavailable", "database", "unreachable"));
  }
}
