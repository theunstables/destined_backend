package com.loveos.api.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.messaging.dto.MessagingResponses;
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

class MessagingControllerContractTest {
  private MessagingService service;
  private MockMvc mvc;
  private UUID userId;
  private UUID messageId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(MessagingService.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new MessagingController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();
    userId = UUID.randomUUID();
    messageId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void sendAcceptsFrontendShapeAndReturnsCreatedMessage() throws Exception {
    when(service.send(eq(userId), any())).thenReturn(
        new MessagingService.SendResult(message(), false, messageId));

    mvc.perform(post("/v1/chat/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"kind":"text","body":"Hello","clientId":"client-12345678"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(messageId.toString()))
        .andExpect(jsonPath("$.data.authorId").value("me"))
        .andExpect(jsonPath("$.data.status").value("sent"));
  }

  @Test
  void duplicateSendReturnsOk() throws Exception {
    when(service.send(eq(userId), any())).thenReturn(
        new MessagingService.SendResult(message(), true, messageId));

    mvc.perform(post("/v1/chat/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"kind":"text","body":"Hello","clientId":"client-12345678"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void emptyTextMessageReturnsValidationEnvelope() throws Exception {
    mvc.perform(post("/v1/chat/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"kind\":\"text\",\"body\":\"   \"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void listReturnsCursorMetadata() throws Exception {
    when(service.list(userId, null, 50))
        .thenReturn(new MessagingService.MessagePage(List.of(message()), "next-id"));

    mvc.perform(get("/v1/chat/messages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].body").value("Hello"))
        .andExpect(jsonPath("$.meta.nextCursor").value("next-id"));
  }

      @Test
      void pinnedAndSearchUseStableMessageShape() throws Exception {
      when(service.pinned(userId)).thenReturn(List.of(message()));
      when(service.search(userId, "hello", 50)).thenReturn(List.of(message()));

      mvc.perform(get("/v1/chat/messages/pinned"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].kind").value("text"));
      mvc.perform(get("/v1/chat/messages/search").queryParam("q", "hello"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(messageId.toString()));
      }

      @Test
      void reactionPinAndReadRoutesReturnUpdatedContracts() throws Exception {
      when(service.toggleReaction(userId, messageId, "❤️")).thenReturn(message());
      when(service.togglePin(userId, messageId)).thenReturn(message());
      when(service.markRead(userId, messageId))
        .thenReturn(new MessagingResponses.ReadResult(List.of(messageId.toString())));

      mvc.perform(post("/v1/chat/messages/{id}/reactions", messageId)
          .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"❤️\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(messageId.toString()));
      mvc.perform(post("/v1/chat/messages/{id}/pin", messageId))
        .andExpect(status().isOk());
      mvc.perform(post("/v1/chat/read").contentType(MediaType.APPLICATION_JSON)
          .content("{\"upToMessageId\":\"" + messageId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.messageIds[0]").value(messageId.toString()));
      }

      @Test
      void deleteReturnsNoContent() throws Exception {
      mvc.perform(delete("/v1/chat/messages/{id}", messageId))
        .andExpect(status().isNoContent());
      verify(service).delete(userId, messageId);
      }

      @Test
      void invalidReactionReturnsValidationEnvelope() throws Exception {
      mvc.perform(post("/v1/chat/messages/{id}/reactions", messageId)
          .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
      }

  private MessagingResponses.MessageDto message() {
    return new MessagingResponses.MessageDto(messageId.toString(), "me", "text", "Hello",
        null, null, null, List.of(), false, "2026-09-18T00:00:00Z", "sent",
        "client-12345678");
  }
}
