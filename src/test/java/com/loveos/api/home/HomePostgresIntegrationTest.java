package com.loveos.api.home;

import static org.assertj.core.api.Assertions.assertThat;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.calendar.CalendarService;
import com.loveos.api.calendar.dto.CalendarRequests;
import com.loveos.api.memories.MemoriesService;
import com.loveos.api.memories.dto.MemoryRequests;
import com.loveos.api.pairing.domain.*;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import com.loveos.api.story.StoryService;
import com.loveos.api.story.dto.StoryRequests;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "loveos.jwt.secret=test-only-secret-with-at-least-32-characters",
    "loveos.mail.driver=console", "spring.flyway.baseline-on-migrate=false"
})
class HomePostgresIntegrationTest {
  @Autowired private HomeService home;
  @Autowired private StoryService story;
  @Autowired private MemoriesService memories;
  @Autowired private CalendarService calendar;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;

  @Test
  void composesPersistedStoryMemoriesCalendarAndCoupleData() {
    String suffix = UUID.randomUUID().toString();
    User alex = user("alex-" + suffix + "@example.test", "Alex", "Al");
    User sam = user("sam-" + suffix + "@example.test", "Sam", null);
    Couple couple = new Couple();
    couple.setName("Our Place");
    couple.setShortName("Us");
    couple.setCoverStyle(CoverStyle.NIGHT);
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);
    member(couple, alex, CoupleRole.FOUNDER);
    member(couple, sam, CoupleRole.MEMBER);

    LocalDate met = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(100);
    story.saveStory(alex.getId(), new StoryRequests.SaveStory(
        new StoryRequests.StoryDate(met.toString(), "exact"), null, null, null,
        new StoryRequests.KeyDates(null, null, null, null, null)));
    memories.create(alex.getId(), new MemoryRequests.CreateMemory(
        "Rome", "2024-06-12", null, "Rome", null,
        "https://example.test/rome.jpg", null, List.of("Trips"), false, null));
    calendar.create(alex.getId(), new CalendarRequests.CreateEvent(
        "Date night", LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3).toString(),
        null, null, null, null, "dateNight", false, null));

    var result = home.getHome(alex.getId());

    assertThat(result.coupleName()).isEqualTo("Al & Sam");
    assertThat(result.space().coverStyle()).isEqualTo("night");
    assertThat(result.daysTogether()).isEqualTo(100);
    assertThat(result.stats()).extracting(stat -> stat.value())
        .contains("1", "1");
    assertThat(result.recentMemories()).singleElement()
        .extracting(memory -> memory.title()).isEqualTo("Rome");
    assertThat(result.comingUp()).anyMatch(item -> item.label().equals("Date night"));
  }

  private User user(String email, String displayName, String nickname) {
    User user = new User();
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setDisplayName(displayName);
    user.setNickname(nickname);
    return users.saveAndFlush(user);
  }

  private void member(Couple couple, User user, CoupleRole role) {
    CoupleMember member = new CoupleMember();
    member.setCoupleId(couple.getId());
    member.setUserId(user.getId());
    member.setRole(role);
    members.saveAndFlush(member);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PostgresTestConfiguration {
    @Bean(destroyMethod = "close") EmbeddedPostgres testPostgres() throws IOException {
      return EmbeddedPostgres.builder().setPort(0).start();
    }
    @Bean @Primary DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }
  }
}
