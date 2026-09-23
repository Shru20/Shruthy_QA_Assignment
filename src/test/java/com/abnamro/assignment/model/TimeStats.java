package com.abnamro.assignment.model;

import com.google.gson.annotations.SerializedName;

/** Represents GitLab issue time tracking statistics. */
public class TimeStats {

  @SerializedName("time_estimate")
  private Long timeEstimate;

  @SerializedName("total_time_spent")
  private Long totalTimeSpent;

  @SerializedName("human_time_estimate")
  private String humanTimeEstimate;

  @SerializedName("human_total_time_spent")
  private String humanTotalTimeSpent;

  public TimeStats() {}

  public Long getTimeEstimate() {
    return timeEstimate;
  }

  public void setTimeEstimate(Long timeEstimate) {
    this.timeEstimate = timeEstimate;
  }

  public Long getTotalTimeSpent() {
    return totalTimeSpent;
  }

  public void setTotalTimeSpent(Long totalTimeSpent) {
    this.totalTimeSpent = totalTimeSpent;
  }

  public String getHumanTimeEstimate() {
    return humanTimeEstimate;
  }

  public void setHumanTimeEstimate(String humanTimeEstimate) {
    this.humanTimeEstimate = humanTimeEstimate;
  }

  public String getHumanTotalTimeSpent() {
    return humanTotalTimeSpent;
  }

  public void setHumanTotalTimeSpent(String humanTotalTimeSpent) {
    this.humanTotalTimeSpent = humanTotalTimeSpent;
  }
}
