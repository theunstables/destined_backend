package com.loveos.api.memories;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.memories.dto.MemoryResponses;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class MemoriesControllerContractTest {
  private MemoriesService service;
  private MockMvc mvc;
  private UUID userId;
  private UUID memoryId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(MemoriesService.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new MemoriesController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter()).build();
    userId = UUID.randomUUID();
    memoryId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void listReturnsCursorMetadata() throws Exception {
    when(service.list(eq(userId), isNull(), eq(50), isNull(), isNull(), isNull(), isNull()))
        .thenReturn(new MemoryResponses.MemoryPage(List.of(memory()), "next-id"));
    mvc.perform(get("/v1/memories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].photoUri").value("https://example.test/one.jpg"))
        .andExpect(jsonPath("$.meta.nextCursor").value("next-id"));
  }

  @Test
  void createAcceptsExistingFrontendShape() throws Exception {
    when(service.create(eq(userId), any())).thenReturn(memory());
    mvc.perform(post("/v1/memories").contentType(MediaType.APPLICATION_JSON).content("""
        {"title":"Rome","date":"2024-06-12","caption":"A day", 
         "photoUri":"https://example.test/one.jpg","tags":["Trips"]}
        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.title").value("Rome"));
  }

  @Test
  void invalidCreateUsesValidationEnvelope() throws Exception {
    mvc.perform(post("/v1/memories").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"\",\"date\":\"June\",\"photoUri\":\"file:///one.jpg\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    mvc.perform(delete("/v1/memories/{id}", memoryId)).andExpect(status().isNoContent());
  }

  @Test
  void privateNoteUpdateReturnsMaskedContractAndValidatesInput() throws Exception {
    when(service.upsertPrivateNote(eq(userId), eq(memoryId), eq("Mine"))).thenReturn(memory());
    mvc.perform(put("/v1/memories/{id}/private-note", memoryId)
            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Mine\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reciprocalNotesRevealed").value(false))
        .andExpect(jsonPath("$.data.partnerPrivateNote").doesNotExist());

    mvc.perform(put("/v1/memories/{id}/private-note", memoryId)
            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"   \"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void privateNoteWithdrawalReturnsUpdatedMemory() throws Exception {
    when(service.withdrawPrivateNote(userId, memoryId)).thenReturn(memory());
    mvc.perform(delete("/v1/memories/{id}/private-note", memoryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(memoryId.toString()));
  }

  private MemoryResponses.MemoryDto memory() {
    return new MemoryResponses.MemoryDto(memoryId.toString(), "Rome", "2024-06-12", "A day",
        null, "https://example.test/one.jpg", List.of("https://example.test/one.jpg"),
        null, List.of("Trips"), false, "Alex", "2026-09-18T00:00:00Z",
        "2026-09-18T00:00:00Z", null, null, false);
  }
}