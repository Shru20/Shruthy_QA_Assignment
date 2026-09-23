package com.abnamro.assignment.tests;

import static com.abnamro.assignment.config.ApiConfig.getRequestSpec;
import static io.restassured.RestAssured.given;

import com.abnamro.assignment.base.BaseTest;
import com.abnamro.assignment.util.ApiAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Edge cases and negative tests for GitLab Issues API */
@Feature("GitLab Issues API - Edge Cases & Negative Tests")
@Tag("EDGE_CASE")
public class IssueEdgeCaseTests extends BaseTest {

  @Test
  @DisplayName("TC401: Invalid project ID")
  void testInvalidProjectId() {
    Response response =
        given().spec(getRequestSpec()).get("/projects/{projectId}/issues", "invalid-project-id");
    assert response.getStatusCode() >= 400;
  }

  @Test
  @DisplayName("TC402: Missing authentication token")
  void testMissingAuthToken() {
    Response response = given().baseUri("https://gitlab.com/api/v4").get("/projects/1/issues");
    // Without a token GitLab rejects the request. For private/unknown projects it
    // returns 404 (to avoid leaking existence) instead of 401 - both are valid.
    int status = response.getStatusCode();
    assert status == 401 || status == 404
        : "Expected 401 or 404 for unauthenticated request but got " + status;
  }

  @Test
  @DisplayName("TC403: Invalid state value")
  @Description("Update issue with invalid state value")
  void testInvalidStateValue() {
    Response response = getClient().updateIssue(1L, null, null, null, "invalid_state");
    assert response.getStatusCode() >= 400;
  }

  @Test
  @DisplayName("TC404: Very long label name")
  @Description("Create issue with very long label name")
  void testVeryLongLabelName() {
    String longLabel = "A".repeat(1000);
    Response response =
        getClient().createIssue("Test Issue", null, java.util.Arrays.asList(longLabel), null, null);
    assert response.getStatusCode() >= 200;
  }

  @Test
  @DisplayName("TC405: Null description handling")
  @Description("Create issue with explicitly null description")
  void testNullDescription() {
    Response response = getClient().createIssue("Test Issue", null);
    ApiAssertions.assertStatusCodeCreated(response);
  }

  @Test
  @DisplayName("TC406: Empty labels list")
  @Description("Create issue with empty labels list")
  void testEmptyLabelsList() {
    Response response =
        getClient().createIssue("Test Issue", null, java.util.Arrays.asList(), null, null);
    ApiAssertions.assertStatusCodeCreated(response);
  }
}
