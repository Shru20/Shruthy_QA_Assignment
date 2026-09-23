package com.abnamro.assignment.client;

import static io.restassured.RestAssured.given;

import com.abnamro.assignment.config.ApiConfig;
import com.abnamro.assignment.model.Issue;
import com.google.gson.Gson;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitLab Issues API Client. Provides methods to interact with GitLab Issues API endpoints.
 * Reference: https://docs.gitlab.com/ee/api/issues.html
 */
public class GitLabIssuesClient {

  private static final Logger logger = LoggerFactory.getLogger(GitLabIssuesClient.class);
  private static final Gson gson = new Gson();
  private final String projectId;

  public GitLabIssuesClient(String projectId) {
    this.projectId = projectId;
  }

  @Step("Create issue with title: {title}")
  public Response createIssue(String title, String description) {
    return createIssue(title, description, null, null, null);
  }

  @Step("Create issue with title: {title}, labels: {labels}")
  public Response createIssue(
      String title, String description, List<String> labels, Long assigneeId, Long milestoneId) {
    logger.info("Creating issue with title: {}", title);

    String body = buildIssuePayload(title, description, labels, assigneeId, milestoneId, null);

    Response response =
        given()
            .spec(ApiConfig.getRequestSpec())
            .body(body)
            .post("/projects/{projectId}/issues", projectId);

    logger.info("Issue creation response status: {}", response.getStatusCode());
    return response;
  }

  @Step("Get issue with IID: {issueIid}")
  public Response getIssue(Long issueIid) {
    logger.info("Fetching issue with IID: {}", issueIid);
    return given()
        .spec(ApiConfig.getRequestSpec())
        .get("/projects/{projectId}/issues/{issueIid}", projectId, issueIid);
  }

  @Step("List all issues in project")
  public Response listIssues() {
    return listIssues(null, null, null);
  }

  @Step("List issues with state: {state}, labels: {labels}")
  public Response listIssues(String state, String labels, String orderBy) {
    logger.info("Listing issues with filters - state: {}, labels: {}", state, labels);

    var request = given().spec(ApiConfig.getRequestSpec());

    if (state != null) {
      request.queryParam("state", state);
    }
    if (labels != null) {
      request.queryParam("labels", labels);
    }
    if (orderBy != null) {
      request.queryParam("order_by", orderBy);
    }

    return request.get("/projects/{projectId}/issues", projectId);
  }

  @Step("Update issue IID: {issueIid}")
  public Response updateIssue(
      Long issueIid, String title, String description, List<String> labels, String state) {
    logger.info("Updating issue with IID: {}", issueIid);

    String body = buildIssuePayload(title, description, labels, null, null, state);

    return given()
        .spec(ApiConfig.getRequestSpec())
        .body(body)
        .put("/projects/{projectId}/issues/{issueIid}", projectId, issueIid);
  }

  @Step("Delete issue with IID: {issueIid}")
  public Response deleteIssue(Long issueIid) {
    logger.info("Deleting issue with IID: {}", issueIid);
    return given()
        .spec(ApiConfig.getRequestSpec())
        .delete("/projects/{projectId}/issues/{issueIid}", projectId, issueIid);
  }

  @Step("Close issue with IID: {issueIid}")
  public Response closeIssue(Long issueIid) {
    return updateIssue(issueIid, null, null, null, "closed");
  }

  @Step("Reopen issue with IID: {issueIid}")
  public Response reopenIssue(Long issueIid) {
    return updateIssue(issueIid, null, null, null, "opened");
  }

  /**
   * Build JSON payload for issue creation/update. Only includes non-null fields to accommodate
   * different operations.
   */
  private String buildIssuePayload(
      String title,
      String description,
      List<String> labels,
      Long assigneeId,
      Long milestoneId,
      String state) {
    var payload = new java.util.HashMap<String, Object>();

    if (title != null) payload.put("title", title);
    if (description != null) payload.put("description", description);
    if (labels != null && !labels.isEmpty()) payload.put("labels", String.join(",", labels));
    if (assigneeId != null) payload.put("assignee_id", assigneeId);
    if (milestoneId != null) payload.put("milestone_id", milestoneId);
    // GitLab changes issue state via 'state_event' (close/reopen), not 'state'.
    // Accept either the target state ("closed"/"opened") or an event ("close"/"reopen").
    if (state != null) {
      payload.put("state_event", mapToStateEvent(state));
    }

    return gson.toJson(payload);
  }

  /**
   * Map a desired state or event to a valid GitLab {@code state_event} value. Unrecognized values
   * are forwarded as-is so the API rejects them with a 400, which negative tests rely on.
   */
  private String mapToStateEvent(String state) {
    switch (state.toLowerCase()) {
      case "closed":
      case "close":
        return "close";
      case "opened":
      case "reopen":
        return "reopen";
      default:
        return state;
    }
  }

  /** Parse Issue object from response. */
  public Issue parseIssueFromResponse(Response response) {
    return gson.fromJson(response.getBody().asString(), Issue.class);
  }

  /** Parse list of Issues from response. */
  public List<Issue> parseIssuesFromResponse(Response response) {
    Issue[] issues = gson.fromJson(response.getBody().asString(), Issue[].class);
    return Arrays.asList(issues);
  }
}
