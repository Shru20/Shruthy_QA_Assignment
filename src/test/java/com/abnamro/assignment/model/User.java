package com.abnamro.assignment.model;

import com.google.gson.annotations.SerializedName;

/** Represents a GitLab User (e.g. issue author or assignee). */
public class User {

  @SerializedName("id")
  private Long id;

  @SerializedName("username")
  private String username;

  @SerializedName("name")
  private String name;

  @SerializedName("state")
  private String state;

  @SerializedName("web_url")
  private String webUrl;

  public User() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getWebUrl() {
    return webUrl;
  }

  public void setWebUrl(String webUrl) {
    this.webUrl = webUrl;
  }
}
