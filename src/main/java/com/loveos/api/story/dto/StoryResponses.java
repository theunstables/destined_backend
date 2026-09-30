package com.loveos.api.story.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class StoryResponses {

  private StoryResponses() {}

  public record StoryDate(String value, String precision) {}

  public record Moment(
      @JsonInclude(JsonInclude.Include.NON_NULL) String date,
      @JsonInclude(JsonInclude.Include.NON_NULL) String location,
      @JsonInclude(JsonInclude.Include.NON_NULL) String note,
      @JsonInclude(JsonInclude.Include.NON_NULL) String photoUri) {}

  public record KeyDates(
      @JsonInclude(JsonInclude.Include.NON_NULL) String anniversary,
      @JsonInclude(JsonInclude.Include.NON_NULL) String yourBirthday,
      @JsonInclude(JsonInclude.Include.NON_NULL) String partnerBirthday,
      @JsonInclude(JsonInclude.Include.NON_NULL) String firstDate,
      @JsonInclude(JsonInclude.Include.NON_NULL) String firstMeeting) {}

  public record StoryDto(
      @JsonInclude(JsonInclude.Include.NON_NULL) StoryDate met,
      @JsonInclude(JsonInclude.Include.NON_NULL) Moment firstDate,
      @JsonInclude(JsonInclude.Include.NON_NULL) Moment becameUs,
      @JsonInclude(JsonInclude.Include.NON_NULL) Moment firstMemory,
      KeyDates keyDates) {}
}