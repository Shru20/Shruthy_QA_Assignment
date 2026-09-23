package com.abnamro.assignment.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Deserialised GitLab issue.
 *
 * <p>Read-only by design: Gson populates fields reflectively, so no setters are needed and tests
 * cannot accidentally mutate a parsed response. Timestamps stay as raw ISO-8601 strings, which is
 * what assertions compare against.
 */
public class Issue {

  @SerializedName("id")
  private Long id;

  @SerializedName("iid")
  private Long iid;

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

  public Long getId() {
    return id;
  }

  public Long getIid() {
    return iid;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getState() {
    return state;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public List<String> getLabels() {
    return labels;
  }
}
