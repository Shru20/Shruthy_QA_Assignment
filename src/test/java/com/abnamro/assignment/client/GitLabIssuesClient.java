package com.abnamro.assignment.client;

import static io.restassured.RestAssured.given;

import com.abnamro.assignment.config.ApiConfig;
import com.abnamro.assignment.model.Issue;
import com.google.gson.Gson;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the GitLab Issues API.
 *
 * <p>Reference: <a href="https://docs.gitlab.com/ee/api/issues.html">GitLab Issues API</a>
 *
 * <p>Most methods use the configured authenticated specification from {@link ApiConfig}. A few
 * variants exist purely for negative testing — requests sent without credentials, with a
 * caller-supplied token, or against a project other than the configured one. They live here rather
 * than being assembled ad hoc in tests so every request in the suite shares the same base URI,
 * content type and filters, and differs only in the dimension under test.
 *
 * <p>Logging is at DEBUG. Allure {@code @Step} annotations already record each call in the report,
 * so INFO-level logging would duplicate that into the build output.
 */
public class GitLabIssuesClient {

  private static final Logger logger = LoggerFactory.getLogger(GitLabIssuesClient.class);
  private static final Gson GSON = new Gson();

  /** Collection endpoint. The placeholder is bound positionally by REST Assured. */
  private static final String ISSUES_PATH = "/projects/{projectId}/issues";

  /** Single-issue endpoint. */
  private static final String ISSUE_PATH = "/projects/{projectId}/issues/{issueIid}";

  /**
   * Variants that address a project other than the configured one. The placeholder is named
   * differently so the call site reads correctly, even though binding is positional either way.
   */
  private static final String ISSUES_PATH_IN_PROJECT = "/projects/{targetProjectId}/issues";

  private static final String ISSUE_PATH_IN_PROJECT =
      "/projects/{targetProjectId}/issues/{issueIid}";

  private final String projectId;

  public GitLabIssuesClient(String projectId) {
    this.projectId = projectId;
  }

  // ---------------------------------------------------------------------------------
  // Create
  // ---------------------------------------------------------------------------------

  /**
   * Create an issue with a title and optional description.
   *
   * @param title Issue title
   * @param description Issue description; omitted from the payload when null
   * @return response; 201 expected
   */
  @Step("Create issue with title: {title}")
  public Response createIssue(String title, String description) {
    return createIssue(title, description, null, null, null);
  }

  /**
   * Create a fully specified issue. Null arguments are omitted from the payload rather than sent as
   * explicit nulls, so this method serves both minimal and complete creation.
   *
   * @param title Issue title
   * @param description Issue description; may be null
   * @param labels Labels to apply; may be null or empty
   * @return response; 201 expected
   */
  @Step("Create issue with title: {title}, labels: {labels}")
  public Response createIssue(
      String title, String description, List<String> labels, Long assigneeId, Long milestoneId) {
    logger.debug("Creating issue with title: {}", title);
    String body = buildIssuePayload(title, description, labels, assigneeId, milestoneId, null);
    return authenticated().body(body).post(ISSUES_PATH, projectId);
  }

  /**
   * Create an issue from an arbitrary parameter map.
   *
   * <p>Covers fields the typed overloads cannot express — {@code confidential}, {@code issue_type},
   * raw comma-delimited label strings — and deliberately invalid values.
   *
   * @param params Parameters to serialise as the JSON body
   * @return response
   */
  @Step("Create issue with parameters: {params}")
  public Response createIssue(Map<String, Object> params) {
    logger.debug("Creating issue with parameters: {}", params);
    return authenticated().body(GSON.toJson(params)).post(ISSUES_PATH, projectId);
  }

  /**
   * Create an issue from a raw request body, bypassing serialisation.
   *
   * <p>The only way to send a syntactically invalid payload: a parameter map can always be
   * serialised into well-formed JSON.
   *
   * @param rawBody Body to send verbatim
   * @return response
   */
  @Step("Create issue with raw body")
  public Response createIssueWithRawBody(String rawBody) {
    logger.debug("Creating issue with raw body: {}", rawBody);
    return authenticated().body(rawBody).post(ISSUES_PATH, projectId);
  }

  // ---------------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------------

  /**
   * Fetch a single issue by its project-scoped iid.
   *
   * @param issueIid Project-scoped issue iid
   * @return response; 200 expected
   */
  @Step("Get issue with IID: {issueIid}")
  public Response getIssue(Long issueIid) {
    logger.debug("Fetching issue IID: {}", issueIid);
    return authenticated().get(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Fetch an issue with no credentials attached, for tests asserting that a private project refuses
   * anonymous reads.
   *
   * @param issueIid Project-scoped issue iid
   * @return response; 401 expected against a private project
   */
  @Step("Get issue IID: {issueIid} without authentication")
  public Response getIssueWithoutAuth(Long issueIid) {
    logger.debug("Attempting unauthenticated read of issue IID: {}", issueIid);
    return unauthenticated().get(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Fetch an issue from an explicitly supplied project rather than the configured one.
   *
   * <p>Lets tests target a non-existent project, or one the token cannot see, to confirm project
   * scope is resolved and enforced before issue lookup.
   *
   * @param targetProjectId Project addressed; a numeric id or URL-encoded {@code group/project}
   * @param issueIid Project-scoped issue iid
   * @return response
   */
  @Step("Get issue IID: {issueIid} from project: {targetProjectId}")
  public Response getIssueInProject(String targetProjectId, Long issueIid) {
    logger.debug("Fetching issue IID: {} from project: {}", issueIid, targetProjectId);
    return authenticated().get(ISSUE_PATH_IN_PROJECT, targetProjectId, issueIid);
  }

  /**
   * List every issue in the configured project, unfiltered.
   *
   * @return response containing a top-level JSON array
   */
  @Step("List all issues in project")
  public Response listIssues() {
    return listIssues(Map.of());
  }

  /**
   * List issues filtered by state, labels and sort order. Null arguments are omitted from the query
   * string, so any subset of the three may be supplied.
   *
   * @param state State filter, e.g. {@code opened} or {@code closed}; may be null
   * @param labels Comma-separated label filter; may be null
   * @param orderBy Sort field, e.g. {@code created_at}; may be null
   * @return response containing a top-level JSON array
   */
  @Step("List issues with state: {state}, labels: {labels}")
  public Response listIssues(String state, String labels, String orderBy) {
    Map<String, Object> params = new HashMap<>();
    if (state != null) {
      params.put("state", state);
    }
    if (labels != null) {
      params.put("labels", labels);
    }
    if (orderBy != null) {
      params.put("order_by", orderBy);
    }
    return listIssues(params);
  }

  /**
   * List issues with arbitrary query parameters.
   *
   * <p>Generic by design: pagination, sorting, search and scope are all query parameters, and a
   * method per combination would bloat the client without adding type safety. Tests name the
   * parameters they exercise.
   *
   * @param queryParams Parameters to apply; may be empty
   * @return response containing a top-level JSON array
   */
  @Step("List issues with query parameters: {queryParams}")
  public Response listIssues(Map<String, Object> queryParams) {
    logger.debug("Listing issues with parameters: {}", queryParams);
    return authenticated().queryParams(queryParams).get(ISSUES_PATH, projectId);
  }

  /**
   * List issues filtered to a single iid.
   *
   * <p>Uses {@code iids[]} rather than {@code search}: an exact match on an indexed column is
   * immediately consistent, whereas full-text search is not guaranteed read-your-writes and would
   * make assertions on freshly written data flaky.
   *
   * @param issueIid Project-scoped issue iid to filter on
   * @return response containing a JSON array of zero or one element
   */
  @Step("List issues filtered by IID: {issueIid}")
  public Response listIssuesByIid(Long issueIid) {
    logger.debug("Listing issues filtered to IID: {}", issueIid);
    return authenticated().queryParam("iids[]", issueIid).get(ISSUES_PATH, projectId);
  }

  /**
   * List issues from an explicitly supplied project rather than the configured one.
   *
   * @param targetProjectId Project addressed; may be malformed or non-existent
   * @return response
   */
  @Step("List issues in project: {targetProjectId}")
  public Response listIssuesInProject(String targetProjectId) {
    logger.debug("Listing issues in project: {}", targetProjectId);
    return authenticated().get(ISSUES_PATH_IN_PROJECT, targetProjectId);
  }

  // ---------------------------------------------------------------------------------
  // Update
  // ---------------------------------------------------------------------------------

  /**
   * Update an issue's title, description, labels or state. Null arguments are omitted, so this
   * performs a partial update of whichever fields are supplied.
   *
   * @param issueIid Project-scoped issue iid
   * @param title New title; may be null
   * @param description New description; may be null
   * @param labels Replacement label set; may be null
   * @param state Target state or event; see {@link #mapToStateEvent}
   * @return response; 200 expected
   */
  @Step("Update issue IID: {issueIid}")
  public Response updateIssue(
      Long issueIid, String title, String description, List<String> labels, String state) {
    logger.debug("Updating issue IID: {}", issueIid);
    String body = buildIssuePayload(title, description, labels, null, null, state);
    return authenticated().body(body).put(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Update an issue with arbitrary parameters.
   *
   * <p>Required for parameters whose semantics differ from wholesale replacement — {@code
   * add_labels}, {@code remove_labels} — and for {@code due_date} and other fields the typed
   * overload does not cover.
   *
   * @param issueIid Project-scoped issue iid
   * @param params Parameters to serialise as the JSON body; may be empty to test the no-op case
   * @return response
   */
  @Step("Update issue IID: {issueIid} with parameters: {params}")
  public Response updateIssue(Long issueIid, Map<String, Object> params) {
    logger.debug("Updating issue IID: {} with parameters: {}", issueIid, params);
    return authenticated().body(GSON.toJson(params)).put(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Attempt to update an issue with no credentials attached.
   *
   * @param issueIid Project-scoped issue iid
   * @param params Parameters to send
   * @return response; 401 expected
   */
  @Step("Update issue IID: {issueIid} without authentication")
  public Response updateIssueWithoutAuth(Long issueIid, Map<String, Object> params) {
    logger.debug("Attempting unauthenticated update of issue IID: {}", issueIid);
    return unauthenticated().body(GSON.toJson(params)).put(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Close an issue.
   *
   * @param issueIid Project-scoped issue iid
   * @return response; 200 expected
   */
  @Step("Close issue with IID: {issueIid}")
  public Response closeIssue(Long issueIid) {
    return updateIssue(issueIid, null, null, null, "closed");
  }

  /**
   * Reopen a closed issue.
   *
   * @param issueIid Project-scoped issue iid
   * @return response; 200 expected
   */
  @Step("Reopen issue with IID: {issueIid}")
  public Response reopenIssue(Long issueIid) {
    return updateIssue(issueIid, null, null, null, "opened");
  }

  // ---------------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------------

  /**
   * Delete an issue. Requires the Owner role on the project.
   *
   * @param issueIid Project-scoped issue iid
   * @return response; 204 expected
   */
  @Step("Delete issue with IID: {issueIid}")
  public Response deleteIssue(Long issueIid) {
    logger.debug("Deleting issue IID: {}", issueIid);
    return authenticated().delete(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Attempt to delete an issue with no credentials attached.
   *
   * <p>Backs tests asserting a destructive call is refused at the authentication layer, before any
   * resource lookup occurs.
   *
   * @param issueIid Project-scoped issue iid
   * @return response; 401 expected
   */
  @Step("Delete issue IID: {issueIid} without authentication")
  public Response deleteIssueWithoutAuth(Long issueIid) {
    logger.debug("Attempting unauthenticated delete of issue IID: {}", issueIid);
    return unauthenticated().delete(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Attempt to delete an issue using a caller-supplied token.
   *
   * <p>Exercises malformed, revoked or under-scoped credentials without mutating the configured
   * token, which would leak state into sibling tests.
   *
   * @param issueIid Project-scoped issue iid
   * @param token Token to present; may be invalid
   * @return response
   */
  @Step("Delete issue IID: {issueIid} with a supplied token")
  public Response deleteIssueWithToken(Long issueIid, String token) {
    logger.debug("Attempting delete of issue IID: {} with a caller-supplied token", issueIid);
    return withToken(token).delete(ISSUE_PATH, projectId, issueIid);
  }

  /**
   * Delete an issue from an explicitly supplied project rather than the configured one.
   *
   * @param targetProjectId Project addressed; a numeric id or URL-encoded {@code group/project}
   * @param issueIid Project-scoped issue iid
   * @return response
   */
  @Step("Delete issue IID: {issueIid} from project: {targetProjectId}")
  public Response deleteIssueInProject(String targetProjectId, Long issueIid) {
    logger.debug("Attempting delete of issue IID: {} in project: {}", issueIid, targetProjectId);
    return authenticated().delete(ISSUE_PATH_IN_PROJECT, targetProjectId, issueIid);
  }

  // ---------------------------------------------------------------------------------
  // Response parsing
  // ---------------------------------------------------------------------------------

  /**
   * Parse a single issue from a response body.
   *
   * @param response Response whose body is a JSON object
   * @return the deserialised issue
   */
  public Issue parseIssueFromResponse(Response response) {
    return GSON.fromJson(response.getBody().asString(), Issue.class);
  }

  /**
   * Parse a list of issues from a response body.
   *
   * <p>Returns an empty list rather than null when the body is not a JSON array, so a caller
   * asserting on an unexpected error payload gets a clear assertion failure instead of an NPE.
   *
   * @param response Response whose body is a JSON array
   * @return the deserialised issues, never null
   */
  public List<Issue> parseIssuesFromResponse(Response response) {
    Issue[] issues = GSON.fromJson(response.getBody().asString(), Issue[].class);
    return issues == null ? List.of() : Arrays.asList(issues);
  }

  // ---------------------------------------------------------------------------------
  // Request specifications
  // ---------------------------------------------------------------------------------

  /** Request carrying the configured credentials. */
  private RequestSpecification authenticated() {
    return given().spec(ApiConfig.getRequestSpec());
  }

  /** Request with no credentials, for authentication-layer tests. */
  private RequestSpecification unauthenticated() {
    return given().spec(ApiConfig.getNoAuthRequestSpec());
  }

  /** Request carrying a caller-supplied token, for invalid-credential tests. */
  private RequestSpecification withToken(String token) {
    return given().spec(ApiConfig.getRequestSpecWithToken(token));
  }

  // ---------------------------------------------------------------------------------
  // Payload construction
  // ---------------------------------------------------------------------------------

  /**
   * Build a JSON payload containing only the non-null fields.
   *
   * <p>Omitting nulls rather than sending them is what makes the typed create and update methods
   * usable for partial updates: the API treats an absent key as "leave unchanged" and an explicit
   * null as "clear".
   */
  private String buildIssuePayload(
      String title,
      String description,
      List<String> labels,
      Long assigneeId,
      Long milestoneId,
      String state) {
    Map<String, Object> payload = new HashMap<>();
    if (title != null) {
      payload.put("title", title);
    }
    if (description != null) {
      payload.put("description", description);
    }
    if (labels != null && !labels.isEmpty()) {
      payload.put("labels", String.join(",", labels));
    }
    if (assigneeId != null) {
      payload.put("assignee_id", assigneeId);
    }
    if (milestoneId != null) {
      payload.put("milestone_id", milestoneId);
    }
    if (state != null) {
      payload.put("state_event", mapToStateEvent(state));
    }
    return GSON.toJson(payload);
  }

  /**
   * Map a desired state or event to a valid GitLab {@code state_event} value.
   *
   * <p>GitLab transitions state via {@code state_event} ({@code close} / {@code reopen}), not via
   * the {@code state} field it returns. Accepting either form lets callers express the intent
   * naturally. Unrecognised values are forwarded verbatim so the API rejects them with a 400, which
   * the negative tests depend on.
   */
  private String mapToStateEvent(String state) {
    return switch (state.toLowerCase()) {
      case "closed", "close" -> "close";
      case "opened", "reopen" -> "reopen";
      default -> state;
    };
  }
}
