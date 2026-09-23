package com.abnamro.assignment.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * DTO for creating or updating GitLab Issues via API. Plain POJO (no Lombok) with a fluent builder.
 */
public class IssueRequestDto {

  @SerializedName("title")
  private String title;

  @SerializedName("description")
  private String description;

  @SerializedName("labels")
  private List<String> labels;

  @SerializedName("assigned_to_id")
  private Long assignedToId;

  @SerializedName("milestone_id")
  private Long milestoneId;

  @SerializedName("state")
  private String state;

  @SerializedName("confidential")
  private Boolean confidential;

  @SerializedName("due_date")
  private String dueDate;

  @SerializedName("discussion_locked")
  private Boolean discussionLocked;

  public IssueRequestDto() {}

  public IssueRequestDto(
      String title,
      String description,
      List<String> labels,
      Long assignedToId,
      Long milestoneId,
      String state,
      Boolean confidential,
      String dueDate,
      Boolean discussionLocked) {
    this.title = title;
    this.description = description;
    this.labels = labels;
    this.assignedToId = assignedToId;
    this.milestoneId = milestoneId;
    this.state = state;
    this.confidential = confidential;
    this.dueDate = dueDate;
    this.discussionLocked = discussionLocked;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getLabels() {
    return labels;
  }

  public void setLabels(List<String> labels) {
    this.labels = labels;
  }

  public Long getAssignedToId() {
    return assignedToId;
  }

  public void setAssignedToId(Long assignedToId) {
    this.assignedToId = assignedToId;
  }

  public Long getMilestoneId() {
    return milestoneId;
  }

  public void setMilestoneId(Long milestoneId) {
    this.milestoneId = milestoneId;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public Boolean getConfidential() {
    return confidential;
  }

  public void setConfidential(Boolean confidential) {
    this.confidential = confidential;
  }

  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }

  public Boolean getDiscussionLocked() {
    return discussionLocked;
  }

  public void setDiscussionLocked(Boolean discussionLocked) {
    this.discussionLocked = discussionLocked;
  }

  /** Fluent builder for {@link IssueRequestDto}. */
  public static class Builder {
    private String title;
    private String description;
    private List<String> labels;
    private Long assignedToId;
    private Long milestoneId;
    private String state;
    private Boolean confidential;
    private String dueDate;
    private Boolean discussionLocked;

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder labels(List<String> labels) {
      this.labels = labels;
      return this;
    }

    public Builder assignedToId(Long assignedToId) {
      this.assignedToId = assignedToId;
      return this;
    }

    public Builder milestoneId(Long milestoneId) {
      this.milestoneId = milestoneId;
      return this;
    }

    public Builder state(String state) {
      this.state = state;
      return this;
    }

    public Builder confidential(Boolean confidential) {
      this.confidential = confidential;
      return this;
    }

    public Builder dueDate(String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    public Builder discussionLocked(Boolean discussionLocked) {
      this.discussionLocked = discussionLocked;
      return this;
    }

    public IssueRequestDto build() {
      return new IssueRequestDto(
          title,
          description,
          labels,
          assignedToId,
          milestoneId,
          state,
          confidential,
          dueDate,
          discussionLocked);
    }
  }
}
