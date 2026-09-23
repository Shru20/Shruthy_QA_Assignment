package com.abnamro.assignment.tests;

import com.abnamro.assignment.base.BaseTest;
import com.abnamro.assignment.model.Issue;
import com.abnamro.assignment.util.ApiAssertions;
import com.abnamro.assignment.util.TestDataGenerator;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** UPDATE operation tests for GitLab Issues API */
@Feature("GitLab Issues API - UPDATE")
@Tag("UPDATE")
public class IssueUpdateTests extends BaseTest {

  @Test
  @DisplayName("TC201: Update issue title")
  @Description("Update title of an existing issue")
  void testUpdateIssueTitle() {
    String initialTitle = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(initialTitle, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    String newTitle = "UPDATED: " + TestDataGenerator.generateIssueTitle();
    Response updateResponse = getClient().updateIssue(issueIid, newTitle, null, null, null);
    ApiAssertions.assertStatusCodeOk(updateResponse);

    Issue updatedIssue = getClient().parseIssueFromResponse(updateResponse);
    assert updatedIssue.getTitle().equals(newTitle);
  }

  @Test
  @DisplayName("TC202: Update issue description")
  @Description("Update description of an existing issue")
  void testUpdateIssueDescription() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    String newDescription = "Updated: " + TestDataGenerator.generateIssueDescription();
    Response updateResponse = getClient().updateIssue(issueIid, null, newDescription, null, null);
    ApiAssertions.assertStatusCodeOk(updateResponse);

    Issue updatedIssue = getClient().parseIssueFromResponse(updateResponse);
    assert updatedIssue.getDescription().equals(newDescription);
  }

  @Test
  @DisplayName("TC203: Close an open issue")
  @Description("Close an opened issue by setting state to closed")
  void testCloseIssue() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    Response closeResponse = getClient().closeIssue(issueIid);
    ApiAssertions.assertStatusCodeOk(closeResponse);

    Issue closedIssue = getClient().parseIssueFromResponse(closeResponse);
    assert closedIssue.getState().equals("closed");
  }

  @Test
  @DisplayName("TC204: Reopen a closed issue")
  @Description("Reopen a closed issue by setting state to opened")
  void testReopenIssue() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    getClient().closeIssue(issueIid);
    Response reopenResponse = getClient().reopenIssue(issueIid);
    ApiAssertions.assertStatusCodeOk(reopenResponse);

    Issue reopenedIssue = getClient().parseIssueFromResponse(reopenResponse);
    assert reopenedIssue.getState().equals("opened");
  }

  @Test
  @DisplayName("TC205: Update issue labels")
  @Description("Update labels assigned to an issue")
  void testUpdateIssueLabels() {
    String title = TestDataGenerator.generateIssueTitle();
    List<String> originalLabels = Arrays.asList("label1", "label2");
    Response createResponse = getClient().createIssue(title, null, originalLabels, null, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    List<String> newLabels = Arrays.asList("updated", "labels");
    Response updateResponse = getClient().updateIssue(issueIid, null, null, newLabels, null);
    ApiAssertions.assertStatusCodeOk(updateResponse);

    Issue updatedIssue = getClient().parseIssueFromResponse(updateResponse);
    assert updatedIssue.getLabels() != null;
  }

  @Test
  @DisplayName("TC206: Update multiple fields")
  @Description("Update title, description and labels in single call")
  void testUpdateMultipleFields() {
    String title = TestDataGenerator.generateIssueTitle();
    Response createResponse = getClient().createIssue(title, null);
    Issue createdIssue = getClient().parseIssueFromResponse(createResponse);
    Long issueIid = createdIssue.getIid();

    String newTitle = "Updated Title";
    String newDescription = "Updated Description";
    List<String> newLabels = Arrays.asList("bug", "fixed");

    Response updateResponse =
        getClient().updateIssue(issueIid, newTitle, newDescription, newLabels, null);
    ApiAssertions.assertStatusCodeOk(updateResponse);

    Issue updatedIssue = getClient().parseIssueFromResponse(updateResponse);
    assert updatedIssue.getTitle().equals(newTitle);
    assert updatedIssue.getDescription().equals(newDescription);
  }
}
