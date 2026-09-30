package com.loveos.api.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MediaControllerContractTest {

  private MediaService service;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(MediaService.class);
    mvc = MockMvcBuilders.standaloneSetup(new MediaController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();
    userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void postAcceptsFilePartAndReturnsUploadContract() throws Exception {
    when(service.upload(eq(userId), any())).thenReturn(new MediaService.UploadedMedia(
        "http://localhost:4000/media/3cd94be6-4665-4ebc-8eac-f84bf1dfa678.jpg",
        "image/jpeg", 3));
    var file = new MockMultipartFile("file", "ignored.jpg", "image/jpeg",
        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    mvc.perform(multipart("/v1/media").file(file))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.url").value(
            "http://localhost:4000/media/3cd94be6-4665-4ebc-8eac-f84bf1dfa678.jpg"))
        .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
        .andExpect(jsonPath("$.data.bytes").value(3));
  }

  @Test
  void missingFilePartUsesStableErrorEnvelope() throws Exception {
    mvc.perform(multipart("/v1/media"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }
}