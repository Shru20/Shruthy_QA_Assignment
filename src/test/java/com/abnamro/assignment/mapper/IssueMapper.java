package com.abnamro.assignment.mapper;

import com.abnamro.assignment.dto.IssueRequestDto;
import com.abnamro.assignment.model.Issue;

/**
 * Hand-written mapper between {@link Issue} and {@link IssueRequestDto}. Kept free of
 * MapStruct/annotation processing for a robust build.
 */
public final class IssueMapper {

  private IssueMapper() {}

  /** Convert an {@link Issue} model into an {@link IssueRequestDto}. */
  public static IssueRequestDto toRequestDto(Issue issue) {
    if (issue == null) {
      return null;
    }
    return IssueRequestDto.builder()
        .title(issue.getTitle())
        .description(issue.getDescription())
        .labels(issue.getLabels())
        .state(issue.getState())
        .build();
  }

  /** Convert an {@link IssueRequestDto} into an {@link Issue} model. */
  public static Issue toIssue(IssueRequestDto dto) {
    if (dto == null) {
      return null;
    }
    Issue issue = new Issue();
    issue.setTitle(dto.getTitle());
    issue.setDescription(dto.getDescription());
    issue.setLabels(dto.getLabels());
    issue.setState(dto.getState());
    return issue;
  }
}
