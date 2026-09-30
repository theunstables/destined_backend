package com.loveos.api.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleRole;
import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import com.loveos.api.pairing.repo.InviteRepository;
import com.loveos.api.story.dto.StoryRequests;
import com.loveos.api.story.repo.KeyDateRepository;
import com.loveos.api.story.repo.StoryMomentRepository;
import com.loveos.api.story.repo.StoryRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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
    "loveos.mail.driver=console",
    "spring.flyway.baseline-on-migrate=false"
})
class StoryPostgresIntegrationTest {

  @Autowired private StoryService storyService;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;
  @Autowired private InviteRepository invites;
  @Autowired private StoryRepository stories;
  @Autowired private StoryMomentRepository moments;
  @Autowired private KeyDateRepository keyDates;

  private User founder;
  private User member;
  private Couple couple;

  @BeforeEach
  void setUp() {
    keyDates.deleteAll();
    moments.deleteAll();
    stories.deleteAll();
    invites.deleteAll();
    members.deleteAll();
    couples.deleteAll();
    users.deleteAll();

    founder = user("founder@example.test", "Founder");
    member = user("member@example.test", "Member");
    couple = new Couple();
    couple.setStatus(CoupleStatus.PENDING);
    couples.saveAndFlush(couple);
    membership(founder, CoupleRole.FOUNDER);
  }

  @Test
  void pendingFounderCanSaveAndRetryWithoutDuplicates() {
    StoryRequests.SaveStory input = completeStory();

    storyService.saveStory(founder.getId(), input);
    var result = storyService.saveStory(founder.getId(), input);

    assertThat(stories.count()).isEqualTo(1);
    assertThat(moments.count()).isEqualTo(3);
    assertThat(keyDates.count()).isEqualTo(5);
    assertThat(result.met().precision()).isEqualTo("monthYear");
    assertThat(result.firstDate().photoUri()).isEqualTo("https://example.test/first.jpg");
    assertThat(result.keyDates().firstMeeting()).isEqualTo("2020-01-01");
    assertThat(result.keyDates().firstDate()).isEqualTo("2020-07-04");
  }

  @Test
  void putReplacesOmittedValuesAndKeepsOneStory() {
    storyService.saveStory(founder.getId(), completeStory());

    var empty = storyService.saveStory(founder.getId(),
        new StoryRequests.SaveStory(null, null, null, null, null));

    assertThat(stories.count()).isEqualTo(1);
    assertThat(moments.count()).isZero();
    assertThat(keyDates.count()).isZero();
    assertThat(empty.met()).isNull();
    assertThat(empty.keyDates().anniversary()).isNull();
  }

  @Test
  void connectedPartnersSeeBirthdaysRelativeToTheirOwnRole() {
    membership(member, CoupleRole.MEMBER);
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);

    storyService.saveStory(founder.getId(), completeStory());
    var founderView = storyService.getStory(founder.getId());
    var memberView = storyService.getStory(member.getId());

    assertThat(founderView.keyDates().yourBirthday()).isEqualTo("1990-02-03");
    assertThat(founderView.keyDates().partnerBirthday()).isEqualTo("1991-04-05");
    assertThat(memberView.keyDates().yourBirthday()).isEqualTo("1991-04-05");
    assertThat(memberView.keyDates().partnerBirthday()).isEqualTo("1990-02-03");
  }

  @Test
  void noCoupleCannotReadOrWriteStory() {
    User stranger = user("stranger@example.test", "Stranger");

    assertCode(ErrorCode.NO_COUPLE, () -> storyService.getStory(stranger.getId()));
    assertCode(ErrorCode.NO_COUPLE,
        () -> storyService.saveStory(stranger.getId(), completeStory()));
  }

  @Test
  void invalidCalendarDateIsRejectedWithoutPersistence() {
    var invalid = new StoryRequests.SaveStory(
        new StoryRequests.StoryDate("2020-02-31", "exact"), null, null, null, null);

    assertCode(ErrorCode.VALIDATION_ERROR,
        () -> storyService.saveStory(founder.getId(), invalid));
    assertThat(stories.count()).isZero();
  }

  @Test
  void deletingCoupleCascadesThroughStoryMomentsAndKeyDates() {
    storyService.saveStory(founder.getId(), completeStory());

    couples.deleteById(couple.getId());
    couples.flush();

    assertThat(stories.count()).isZero();
    assertThat(moments.count()).isZero();
    assertThat(keyDates.count()).isZero();
  }

  private StoryRequests.SaveStory completeStory() {
    return new StoryRequests.SaveStory(
        new StoryRequests.StoryDate("2020-01-01", "monthYear"),
        new StoryRequests.Moment(
            "2020-07-04", " Rome ", " First date ", "https://example.test/first.jpg"),
        new StoryRequests.Moment("2020-09-12", null, "Became us", null),
        new StoryRequests.Moment("2020-10-01", null, "First memory", null),
        new StoryRequests.KeyDates(
            "2020-09-12", "1990-02-03", "1991-04-05", null, null));
  }

  private User user(String email, String name) {
    User user = new User();
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setDisplayName(name);
    return users.saveAndFlush(user);
  }

  private void membership(User user, CoupleRole role) {
    CoupleMember membership = new CoupleMember();
    membership.setCoupleId(couple.getId());
    membership.setUserId(user.getId());
    membership.setRole(role);
    members.saveAndFlush(membership);
  }

  private static void assertCode(ErrorCode code, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(AppException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PostgresTestConfiguration {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres testPostgres() throws IOException {
      return EmbeddedPostgres.builder().setPort(0).start();
    }

    @Bean
    @Primary
    DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }
  }
}