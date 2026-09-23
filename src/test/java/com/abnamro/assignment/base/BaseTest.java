package com.abnamro.assignment.base;

import com.abnamro.assignment.client.GitLabIssuesClient;
import com.abnamro.assignment.config.ApiConfig;
import com.abnamro.assignment.model.Issue;
import com.abnamro.assignment.util.TestDataGenerator;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for GitLab Issues API tests.
 *
 * <p>Provides three things subclasses rely on: a configured {@link GitLabIssuesClient}, factory
 * methods that create an issue and register it for removal, and teardown that deletes whatever was
 * registered.
 *
 * <p>JUnit creates a fresh instance per test method under the default lifecycle, so the tracking
 * list belongs to exactly one test and needs no synchronisation even when the suite runs in
 * parallel.
 */
public abstract class BaseTest {

  private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);

  /** Status returned by a successful delete; also the status cleanup expects. */
  private static final int HTTP_NO_CONTENT = 204;

  /** Returned when cleanup targets an issue the test already deleted itself. */
  private static final int HTTP_NOT_FOUND = 404;

  private static final int HTTP_CREATED = 201;

  private GitLabIssuesClient issuesClient;

  /** Issues created by the current test, deleted during teardown. */
  private final List<Long> createdIssueIids = new ArrayList<>();

  /**
   * Validates configuration once per test class rather than once per test.
   *
   * <p>Failing here produces a single actionable error. Letting the tests run unconfigured would
   * instead produce dozens of 401s or 404s that look like product defects.
   */
  @BeforeAll
  static void verifyConfiguration() {
    requireConfigured(
        ApiConfig.getProjectId(),
        "GITLAB_API_PROJECT_ID",
        "api.project.id",
        "Without it every request resolves to a non-existent project and returns 404.");
    requireConfigured(
        ApiConfig.getToken(),
        "GITLAB_API_TOKEN",
        "api.token",
        "Without it every request is rejected with 401.");
  }

  @BeforeEach
  void initialiseClient() {
    issuesClient = new GitLabIssuesClient(ApiConfig.getProjectId());
  }

  /**
   * Deletes every issue registered during the test.
   *
   * <p>Cleanup never fails a test. A 404 is routine — many tests delete their own issue as the
   * behaviour under test — and any other outcome is logged rather than thrown, so a housekeeping
   * problem can never be mistaken for a product defect.
   */
  @AfterEach
  void deleteTrackedIssues() {
    for (Long iid : createdIssueIids) {
      try {
        int status = issuesClient.deleteIssue(iid).getStatusCode();
        if (status != HTTP_NO_CONTENT && status != HTTP_NOT_FOUND) {
          logger.warn("Cleanup of issue {} returned unexpected status {}", iid, status);
        }
      } catch (RuntimeException e) {
        logger.warn("Cleanup of issue {} failed: {}", iid, e.getMessage());
      }
    }
    createdIssueIids.clear();
  }

  /**
   * Creates an issue with a unique generated title and registers it for cleanup.
   *
   * @return the created issue
   */
  protected Issue createTrackedIssue() {
    return createTrackedIssue(TestDataGenerator.generateIssueTitle(), null, null);
  }

  /**
   * Creates an issue with the given title and description and registers it for cleanup.
   *
   * @param title Issue title
   * @param description Issue description, may be null
   * @return the created issue
   */
  protected Issue createTrackedIssue(String title, String description) {
    return createTrackedIssue(title, description, null);
  }

  /**
   * Creates a fully specified issue and registers it for cleanup.
   *
   * <p>Throws rather than asserting on failure: a test that cannot establish its preconditions has
   * errored, not failed, and conflating the two sends the reader hunting for a product defect that
   * is really a setup problem.
   *
   * @param title Issue title
   * @param description Issue description, may be null
   * @param labels Labels to apply, may be null or empty
   * @return the created issue
   */
  protected Issue createTrackedIssue(String title, String description, List<String> labels) {
    Response response = issuesClient.createIssue(title, description, labels, null, null);
    if (response.getStatusCode() != HTTP_CREATED) {
      throw new IllegalStateException(
          "Test setup failed: expected "
              + HTTP_CREATED
              + " when creating an issue but got "
              + response.getStatusCode()
              + ". Body: "
              + response.getBody().asString());
    }
    Issue issue = issuesClient.parseIssueFromResponse(response);
    trackIssue(issue.getIid());
    return issue;
  }

  /**
   * Registers an issue for cleanup. Needed when a test creates an issue through a client call this
   * class does not wrap — a raw parameter map, for instance.
   *
   * @param issueIid Project-scoped issue iid; ignored if null
   */
  protected void trackIssue(Long issueIid) {
    if (issueIid != null) {
      createdIssueIids.add(issueIid);
    }
  }

  protected GitLabIssuesClient getClient() {
    return issuesClient;
  }

  /**
   * Fails fast with an actionable message when a required setting is absent.
   *
   * @param value Resolved configuration value
   * @param envVar Environment variable that supplies it
   * @param property Equivalent properties-file key
   * @param consequence What happens if the tests run without it
   */
  private static void requireConfigured(
      String value, String envVar, String property, String consequence) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Missing configuration: set the "
              + envVar
              + " environment variable (or "
              + property
              + " in src/test/resources/application.properties) before running the tests. "
              + consequence);
    }
  }
}
