package com.abnamro.assignment.base;

import com.abnamro.assignment.client.GitLabIssuesClient;
import com.abnamro.assignment.config.ApiConfig;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base test class for GitLab API tests. Provides shared REST Assured setup and a configured {@link
 * GitLabIssuesClient} for all test subclasses.
 */
public abstract class BaseTest {

  protected static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
  protected GitLabIssuesClient issuesClient;
  protected String projectId;

  @BeforeAll
  public static void setupRestAssured() {
    RestAssured.baseURI = ApiConfig.getBaseUrl();
    logger.info("REST Assured configured");
  }

  @BeforeEach
  public void setUp() {
    projectId = ApiConfig.getProjectId();
    String token = ApiConfig.getToken();

    if (projectId == null || projectId.isBlank()) {
      throw new IllegalStateException(
          "GitLab project id is not configured. Set the GITLAB_API_PROJECT_ID environment variable "
              + "(or api.project.id) before running the tests. Missing it causes 404 on every request.");
    }
    if (token == null || token.isBlank()) {
      throw new IllegalStateException(
          "GitLab token is not configured. Set the GITLAB_API_TOKEN environment variable "
              + "(or api.token) before running the tests.");
    }

    issuesClient = new GitLabIssuesClient(projectId);
  }

  protected GitLabIssuesClient getClient() {
    return issuesClient;
  }

  protected String getProjectId() {
    return projectId;
  }
}
