package com.loveos.api.dailyquestions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DailyQuestionDtos {

  private DailyQuestionDtos() {}

  public record SubmitAnswer(@NotBlank @Size(max = 1500) String answer) {}

  public record DailyQuestion(
      String id,
      String date,
      String prompt,
      String status,
      boolean partnerHasAnswered,
      String myAnswer,
      String partnerAnswer,
      String answeredAt,
      String partnerAnsweredAt,
      String memoryId) {}
}
