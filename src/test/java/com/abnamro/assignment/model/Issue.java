package com.abnamro.assignment.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Plain POJO which represents a GitLab Issue object. This class is used to deserialize JSON
 * responses from the GitLab Issues API.
 */
public class Issue {

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

  @SerializedName("created_at")
  private String createdAt;

  @SerializedName("updated_at")
  private String updatedAt;

  @SerializedName("labels")
  private List<String> labels;

  @SerializedName("web_url")
  private String webUrl;

  public Issue() {}

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

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<String> getLabels() {
    return labels;
  }

  public void setLabels(List<String> labels) {
    this.labels = labels;
  }

  public String getWebUrl() {
    return webUrl;
  }

  public void setWebUrl(String webUrl) {
    this.webUrl = webUrl;
  }
}
