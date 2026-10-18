package com.loveos.api.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.loveos.api.core.AppException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PreferencesServiceTest {
  @Mock UserPreferencesRepository repository;

  private final UUID userId = UUID.randomUUID();
  private ObjectMapper mapper;
  private PreferencesService service;

  @BeforeEach
  void setUp() {
    mapper = JsonMapper.builder().build();
    service = new PreferencesService(repository, mapper);
  }

  @Test
  void storesOnlyKnownPreferenceKeys() throws Exception {
    when(repository.findById(userId)).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var saved = service.replace(userId, mapper.readTree(
        "{\"readReceipts\":false,\"reduceMotion\":true}"));

    assertThat(saved.path("readReceipts").asBoolean()).isFalse();
    assertThat(saved.path("reduceMotion").asBoolean()).isTrue();
  }

  @Test
  void rejectsUnknownKeysInsteadOfSilentlyPersistingThem() throws Exception {
    var request = mapper.readTree("{\"readReceipts\":true,\"adminMode\":true}");

    assertThatThrownBy(() -> service.replace(userId, request))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Unsupported preference");
  }
}
