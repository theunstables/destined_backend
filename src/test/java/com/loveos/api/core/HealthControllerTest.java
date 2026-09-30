package com.loveos.api.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

  @Test
  void livenessNeverTouchesDatabase() {
    HealthController controller = new HealthController(mock(DataSource.class));

    assertThat(controller.health()).containsEntry("status", "ok");
  }

  @Test
  void readinessIsOkWhenDatabaseAcceptsConnections() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(2)).thenReturn(true);

    var response = new HealthController(dataSource).readiness();

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("database", "ready");
  }

  @Test
  void readinessIsUnavailableWhenDatabaseCannotBeReached() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable"));

    var response = new HealthController(dataSource).readiness();

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).containsEntry("database", "unreachable");
  }
}
