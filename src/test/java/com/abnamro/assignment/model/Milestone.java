package com.abnamro.assignment.model;

import com.google.gson.annotations.SerializedName;

/** Represents a GitLab Milestone associated with an issue. */
public class Milestone {

  @SerializedName("id")
  private Long id;

  @SerializedName("iid")
  private Long iid;

  @SerializedName("project_id")
  private Long projectId;

  @SerializedName("title")
  private String title;

  @SerializedName("description")
  private String description;

  @SerializedName("state")
  private String state;

  @SerializedName("due_date")
  private String dueDate;

  public Milestone() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getIid() {
    return iid;
  }

  public void setIid(Long iid) {
    this.iid = iid;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
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

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }
}
