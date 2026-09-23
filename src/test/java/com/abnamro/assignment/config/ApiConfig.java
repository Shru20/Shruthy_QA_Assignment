package com.abnamro.assignment.config;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Central API configuration for GitLab Issues tests. Delegates value resolution to {@link
 * TestConfiguration} and builds a reusable REST Assured {@link RequestSpecification}.
 */
public final class ApiConfig {

  private ApiConfig() {}

  /** Fetch base URL of the GitLab API */
  public static String getBaseUrl() {
    return TestConfiguration.getApiBaseUrl();
  }

  /**
   * Personal access token used for authentication. Read directly from the {@code GITLAB_API_TOKEN}
   * environment variable at runtime; falls back to the value resolved from the properties file.
   */
  public static String getToken() {
    String token = System.getenv("GITLAB_API_TOKEN");
    if (token != null && !token.isBlank()) {
      return token;
    }
    return TestConfiguration.getApiToken();
  }

  /**
   * GitLab project id under test. Read directly from the {@code GITLAB_API_PROJECT_ID} environment
   * variable at runtime; falls back to the value resolved from the properties file.
   */
  public static String getProjectId() {
    String projectId = System.getenv("GITLAB_API_PROJECT_ID");
    if (projectId != null && !projectId.isBlank()) {
      return projectId;
    }
    return TestConfiguration.getProjectId();
  }

  /**
   * Build a REST Assured request specification with base URI, auth header, and JSON content type
   * applied.
   */
  public static RequestSpecification getRequestSpec() {
    return new RequestSpecBuilder()
        .setBaseUri(getBaseUrl())
        .addHeader("PRIVATE-TOKEN", getToken())
        .setContentType(ContentType.JSON)
        .setAccept(ContentType.JSON)
        .build();
  }
}
