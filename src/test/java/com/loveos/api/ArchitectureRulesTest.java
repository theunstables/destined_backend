package com.loveos.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.loveos.api.auth.AuthController;
import com.loveos.api.auth.AuthService;
import com.loveos.api.account.AccountController;
import com.loveos.api.account.AccountService;
import com.loveos.api.calendar.CalendarController;
import com.loveos.api.calendar.CalendarService;
import com.loveos.api.dailyquestions.DailyQuestionController;
import com.loveos.api.dailyquestions.DailyQuestionService;
import com.loveos.api.media.MediaController;
import com.loveos.api.media.MediaService;
import com.loveos.api.home.HomeController;
import com.loveos.api.home.HomeService;
import com.loveos.api.memories.MemoriesController;
import com.loveos.api.memories.MemoriesService;
import com.loveos.api.messaging.MessagingController;
import com.loveos.api.messaging.MessagingService;
import com.loveos.api.notifications.NotificationController;
import com.loveos.api.notifications.NotificationService;
import com.loveos.api.pairing.PairingController;
import com.loveos.api.pairing.PairingService;
import com.loveos.api.repairsignal.RepairSignalController;
import com.loveos.api.repairsignal.RepairSignalService;
import com.loveos.api.story.StoryController;
import com.loveos.api.story.StoryService;
import com.loveos.api.timeline.TimelineController;
import com.loveos.api.timeline.TimelineService;
import jakarta.persistence.Entity;
import jakarta.servlet.ServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight dependency rules with no architecture-test runtime dependency. */
class ArchitectureRulesTest {

  @Test
  void controllersDoNotDependOnRepositoriesOrEntities() {
    assertThat(AuthController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(PairingController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(StoryController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(MediaController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(CalendarController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(MemoriesController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(HomeController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(MessagingController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(AccountController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(NotificationController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(TimelineController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(DailyQuestionController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(RepairSignalController.class.isAnnotationPresent(RestController.class)).isTrue();

    for (Class<?> controller : new Class<?>[] {
      AuthController.class, PairingController.class, StoryController.class, MediaController.class,
      CalendarController.class, MemoriesController.class, HomeController.class,
      MessagingController.class, AccountController.class, NotificationController.class,
      TimelineController.class, DailyQuestionController.class, RepairSignalController.class}) {
      for (Field field : controller.getDeclaredFields()) {
        Class<?> type = field.getType();
        assertThat(Repository.class.isAssignableFrom(type))
            .as("controller field %s must not be a repository", field.getName())
            .isFalse();
        assertThat(type.isAnnotationPresent(Entity.class))
            .as("controller field %s must not be an entity", field.getName())
            .isFalse();
      }
    }
  }

  @Test
  void servicePublicApiContainsNoServletOrHttpResponseTypes() {
    for (Class<?> service : new Class<?>[] {
      AuthService.class, PairingService.class, StoryService.class, MediaService.class,
      CalendarService.class, MemoriesService.class, HomeService.class, MessagingService.class,
      AccountService.class, NotificationService.class, TimelineService.class,
      DailyQuestionService.class, RepairSignalService.class}) {
      for (Method method : service.getDeclaredMethods()) {
        if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;

        assertThat(isWebType(method.getReturnType()))
            .as("service return type for %s", method.getName())
            .isFalse();
        assertThat(Arrays.stream(method.getParameterTypes()).noneMatch(this::isWebType))
            .as("service parameters for %s", method.getName())
            .isTrue();
      }
    }
  }

  private boolean isWebType(Class<?> type) {
    return ServletRequest.class.isAssignableFrom(type)
        || type.getPackageName().startsWith("org.springframework.http")
        || type.getPackageName().startsWith("org.springframework.web");
  }
}
