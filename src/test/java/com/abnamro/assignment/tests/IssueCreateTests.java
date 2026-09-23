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

@Feature("GitLab Issues API - CREATE")
@Tag("CREATE")
public class IssueCreateTests extends BaseTest {

  @Test
  @DisplayName("TC001: Create issue with title only")
  @Description("Create issue with minimum required fields")
  @Tag("Smoke")
  void testCreateIssueWithTitleOnly() {
    String title = TestDataGenerator.generateIssueTitle();
    Response response = getClient().createIssue(title, null);
    ApiAssertions.assertStatusCodeCreated(response);
    Issue issue = getClient().parseIssueFromResponse(response);
    assert issue.getTitle().equals(title);
    assert issue.getState().equals("opened");
  }

  @Test
  @DisplayName("TC002: Create issue with description")
  @Description("Create issue with title and description")
  void testCreateIssueWithDescription() {
    String title = TestDataGenerator.generateIssueTitle();
    String description = TestDataGenerator.generateIssueDescription();
    Response response = getClient().createIssue(title, description);
    ApiAssertions.assertStatusCodeCreated(response);
    Issue issue = getClient().parseIssueFromResponse(response);
    assert issue.getTitle().equals(title);
    assert issue.getDescription().equals(description);
  }

  @Test
  @DisplayName("TC003: Create issue with labels")
  @Description("Create issue with multiple labels")
  void testCreateIssueWithLabels() {
    String title = TestDataGenerator.generateIssueTitle();
    List<String> labels = TestDataGenerator.generateLabels(3);
    Response response = getClient().createIssue(title, null, labels, null, null);
    ApiAssertions.assertStatusCodeCreated(response);
    Issue issue = getClient().parseIssueFromResponse(response);
    assert issue.getLabels() != null;
  }

  @Test
  @DisplayName("TC004: Create multiple issues")
  @Description("Create multiple issues in sequence")
  void testCreateMultipleIssues() {
    for (int i = 0; i < 3; i++) {
      String title = "Issue #" + i + ": " + TestDataGenerator.generateIssueTitle();
      Response response = getClient().createIssue(title, null);
      ApiAssertions.assertStatusCodeCreated(response);
    }
  }

  @Test
  @DisplayName("TC005: Create issue with empty title")
  @Description("Verify empty title is rejected")
  void testCreateIssueWithEmptyTitle() {
    Response response = getClient().createIssue("", null);
    assert response.getStatusCode() >= 400;
  }

  @Test
  @DisplayName("TC006: Create issue with special characters")
  @Description("Handle special characters in title")
  void testCreateIssueWithSpecialCharacters() {
    String title = "Issue #1 & <API> {test} [beta]";
    Response response = getClient().createIssue(title, null);
    if (response.getStatusCode() == 201) {
      Issue issue = getClient().parseIssueFromResponse(response);
      assert issue.getTitle() != null;
    }
  }

  @Test
  @DisplayName("TC007: Create issue with HTML content")
  @Description("Handle HTML in description")
  void testCreateIssueWithHtmlContent() {
    String title = TestDataGenerator.generateIssueTitle();
    String description = "<b>Bold</b> and <i>italic</i>";
    Response response = getClient().createIssue(title, description);
    if (response.getStatusCode() == 201) {
      Issue issue = getClient().parseIssueFromResponse(response);
      assert issue.getDescription() != null;
    }
  }
}
