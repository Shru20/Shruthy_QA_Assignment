package com.abnamro.assignment.tests;

import com.abnamro.assignment.base.BaseTest;
import com.abnamro.assignment.model.Issue;
import com.abnamro.assignment.util.ApiAssertions;
import com.abnamro.assignment.util.TestDataGenerator;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Feature("GitLab Issues API - READ")
@Tag("READ")
public class IssueReadTests extends BaseTest {

  @Test
  @DisplayName("TC101: Get issue by IID")
  @Description("Retrieve a specific issue by its IID")
  void testGetIssueByIid() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    ApiAssertions.assertStatusCodeCreated(createResponse);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();
    Response getResponse = getClient().getIssue(issueIid);
    ApiAssertions.assertStatusCodeOk(getResponse);
    Issue fetchedIssue = getClient().parseIssueFromResponse(getResponse);
    assert fetchedIssue.getIid().equals(issueIid);
    assert fetchedIssue.getTitle().equals(title);
  }

  @Test
  @DisplayName("TC102: List all issues")
  @Description("Retrieve list of all issues in project")
  void testListAllIssues() {
    Response response = getClient().listIssues();
    ApiAssertions.assertStatusCodeOk(response);
    List<Issue> issues = getClient().parseIssuesFromResponse(response);
    assert issues != null;
    assert issues.size() >= 0;
  }

  @Test
  @DisplayName("TC103: Get non-existent issue")
  @Description("Verify 404 when fetching non-existent issue")
  void testGetNonExistentIssue() {
    Response response = getClient().getIssue(999999L);
    ApiAssertions.assertStatusCodeNotFound(response);
  }

  @Test
  @DisplayName("TC104: List issues with state filter")
  @Description("List issues filtered by state (opened/closed)")
  void testListIssuesWithStateFilter() {
    Response openedResponse = getClient().listIssues("opened", null, null);
    ApiAssertions.assertStatusCodeOk(openedResponse);
    List<Issue> openedIssues = getClient().parseIssuesFromResponse(openedResponse);
    for (Issue issue : openedIssues) {
      assert issue.getState().equals("opened");
    }
  }

  @Test
  @DisplayName("TC105: Issue contains all expected fields")
  @Description("Verify issue response contains all expected fields")
  void testIssueContainsExpectedFields() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue issue = getClient().parseIssueFromResponse(createResponse);
    assert issue.getId() != null;
    assert issue.getIid() != null;
    assert issue.getTitle() != null;
    assert issue.getState() != null;
    assert issue.getCreatedAt() != null;
    assert issue.getProjectId() != null;
  }
}
