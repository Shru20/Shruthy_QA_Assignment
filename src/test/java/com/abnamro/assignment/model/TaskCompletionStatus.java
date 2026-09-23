package com.abnamro.assignment.model;

import com.google.gson.annotations.SerializedName;

/** Represents GitLab issue task completion status (checklist items). */
public class TaskCompletionStatus {

  @SerializedName("count")
  private Integer count;

  @SerializedName("completed_count")
  private Integer completedCount;

  public TaskCompletionStatus() {}

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public Integer getCompletedCount() {
    return completedCount;
  }

  public void setCompletedCount(Integer completedCount) {
    this.completedCount = completedCount;
  }
}
