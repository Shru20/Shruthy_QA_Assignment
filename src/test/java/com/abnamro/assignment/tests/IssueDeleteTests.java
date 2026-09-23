package com.abnamro.assignment.tests;

import com.abnamro.assignment.base.BaseTest;
import com.abnamro.assignment.model.Issue;
import com.abnamro.assignment.util.TestDataGenerator;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** DELETE operation tests for GitLab Issues API */
@Feature("GitLab Issues API - DELETE")
@Tag("DELETE")
public class IssueDeleteTests extends BaseTest {

  @Test
  @DisplayName("TC301: Delete an existing issue")
  @Description("Delete an issue successfully")
  void testDeleteIssue() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    Response deleteResponse = getClient().deleteIssue(issueIid);
    assert deleteResponse.getStatusCode() == 204 || deleteResponse.getStatusCode() == 200;
  }

  @Test
  @DisplayName("TC302: Delete non-existent issue")
  @Description("Verify error when deleting non-existent issue")
  void testDeleteNonExistentIssue() {
    Response response = getClient().deleteIssue(999999L);
    assert response.getStatusCode() == 404;
  }

  @Test
  @DisplayName("TC303: Cannot fetch deleted issue")
  @Description("Verify deleted issue cannot be retrieved")
  void testFetchDeletedIssue() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    getClient().deleteIssue(issueIid);

    Response getResponse = getClient().getIssue(issueIid);
    assert getResponse.getStatusCode() == 404;
  }

  @Test
  @DisplayName("TC304: Delete multiple issues")
  @Description("Delete multiple issues sequentially")
  void testDeleteMultipleIssues() {
    for (int i = 0; i < 3; i++) {
      String title = "Deletable Issue #" + i;
      Response createResponse = getClient().createIssue(title, null);
      Issue createdIssue = getClient().parseIssueFromResponse(createResponse);

      Response deleteResponse = getClient().deleteIssue(createdIssue.getIid());
      assert deleteResponse.getStatusCode() == 204 || deleteResponse.getStatusCode() == 200;
    }
  }
}
