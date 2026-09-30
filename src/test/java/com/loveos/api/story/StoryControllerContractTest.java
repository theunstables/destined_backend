package com.loveos.api.story;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.story.dto.StoryResponses;
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

class StoryControllerContractTest {

  private StoryService storyService;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    storyService = org.mockito.Mockito.mock(StoryService.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new StoryController(storyService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();

    userId = UUID.randomUUID();
    var principal = new AuthenticatedUser(userId, "alex@example.test", true);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null));
  }

  @Test
  void getReturnsEmptyStoryWithKeyDatesObject() throws Exception {
    when(storyService.getStory(userId)).thenReturn(emptyStory());

    mvc.perform(get("/v1/story"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.keyDates").isMap())
        .andExpect(jsonPath("$.data.met").doesNotExist());
  }

  @Test
  void putAcceptsFrontendShapeAndReturnsServerCopy() throws Exception {
    when(storyService.saveStory(org.mockito.ArgumentMatchers.eq(userId),
        org.mockito.ArgumentMatchers.any())).thenReturn(new StoryResponses.StoryDto(
            new StoryResponses.StoryDate("2020-01-01", "yearOnly"),
            new StoryResponses.Moment(
                "2020-07-04", "Rome", "Our first date", "https://example.test/photo.jpg"),
            null, null,
            new StoryResponses.KeyDates(null, "1990-01-02", "1991-03-04",
                "2020-07-04", "2020-01-01")));

    mvc.perform(put("/v1/story")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "met":{"value":"2020-01-01","precision":"yearOnly"},
                  "firstDate":{"date":"2020-07-04","location":"Rome","note":"Our first date","photoUri":"https://example.test/photo.jpg"},
                  "keyDates":{"yourBirthday":"1990-01-02","partnerBirthday":"1991-03-04"}
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.met.precision").value("yearOnly"))
        .andExpect(jsonPath("$.data.firstDate.location").value("Rome"))
        .andExpect(jsonPath("$.data.keyDates.firstMeeting").value("2020-01-01"));
  }

  @Test
  void invalidNestedFieldsReturnValidationIssues() throws Exception {
    mvc.perform(put("/v1/story")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"met":{"value":"2020","precision":"approximate"},
                 "firstDate":{"photoUri":"file:///private/photo.jpg"}}
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.issues").isArray());
  }

  private static StoryResponses.StoryDto emptyStory() {
    return new StoryResponses.StoryDto(
        null, null, null, null, new StoryResponses.KeyDates(null, null, null, null, null));
  }
}