package com.abnamro.assignment.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.abnamro.assignment.base.BaseTest;
import com.abnamro.assignment.model.Issue;
import com.abnamro.assignment.util.ApiAssertions;
import com.abnamro.assignment.util.TestDataGenerator;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * CREATE operation tests for the GitLab Issues API.
 *
 * <p>Endpoint under test: {@code POST /projects/:id/issues}
 *
 * <p>Successful creations are registered for teardown through {@link #createAndTrack}, which
 * returns the raw response so tests can assert on status, headers and server-generated fields.
 * Tests expecting a rejection call the client directly — there is nothing to clean up.
 */
@Feature("GitLab Issues API - CREATE")
@Tag("CREATE")
class IssueCreateTests extends BaseTest {

  /** GitLab enforces a 255-character maximum on issue titles. */
  private static final int MAX_TITLE_LENGTH = 255;

  private static final int MAX_DESCRIPTION_LENGTH = 1_048_576;

  // ---------------------------------------------------------------------------------
  // Happy path
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC001: Create issue with title only")
  @Description(
      "Creating an issue with only the mandatory 'title' field must return 201, open the "
          + "issue by default, and populate every server-generated field. Those fields are asserted "
          + "at the JSON level so one lost from the payload cannot be mistaken for one lost in "
          + "deserialisation.")
  @Tag("Smoke")
  void testCreateIssueWithTitleOnly() {
    String title = TestDataGenerator.generateIssueTitle();

    Response response = createAndTrack(title, null);

    ApiAssertions.assertContentTypeJson(response);
    ApiAssertions.assertFieldsNotNull(
        response, "id", "iid", "project_id", "created_at", "web_url", "author.username");

    Issue issue = getClient().parseIssueFromResponse(response);
    assertThat(issue.getTitle()).as("Title should be persisted as submitted").isEqualTo(title);
    assertThat(issue.getState()).as("A new issue must default to 'opened'").isEqualTo("opened");
    assertThat(issue.getDescription()).as("Description was not supplied").isNullOrEmpty();
  }

  @Test
  @DisplayName("TC002: Create issue with labels")
  @Description(
      "Labels supplied at creation must be returned in full. Asserting the exact set "
          + "rather than mere presence catches both losses and unexpected additions — a default label "
          + "applied by a project template would otherwise pass unnoticed.")
  void testCreateIssueWithLabels() {
    List<String> labels = TestDataGenerator.generateLabels(3);

    Response response =
        createAndTrack(
            getClient()
                .createIssue(TestDataGenerator.generateIssueTitle(), null, labels, null, null));

    Issue issue = getClient().parseIssueFromResponse(response);
    assertThat(issue.getLabels())
        .as("All submitted labels should be attached, with no extras")
        .containsExactlyInAnyOrderElementsOf(labels);
  }

  @Test
  @DisplayName("TC003: Create issue with HTML content in description")
  @Description(
      "HTML in the description must be stored verbatim. GitLab renders markdown at "
          + "display time, so escaping or stripping on write would corrupt legitimate content — code "
          + "samples in a bug report being the obvious case.")
  void testCreateIssueWithHtmlContent() {
    String description = "<b>Bold</b> and <i>italic</i>";

    Response response = createAndTrack(TestDataGenerator.generateIssueTitle(), description);

    assertThat(getClient().parseIssueFromResponse(response).getDescription())
        .as("HTML should round-trip unmodified")
        .isEqualTo(description);
  }

  @Test
  @DisplayName("TC004: Create two issues with identical titles")
  @Description(
      "GitLab does not enforce title uniqueness within a project. Both creations must "
          + "succeed and receive distinct identifiers. This pins a real contract decision: were "
          + "uniqueness introduced, client workflows that create recurring issues from a template "
          + "would break.")
  void testCreateTwoIssuesWithIdenticalTitles() {
    String sharedTitle = TestDataGenerator.generateIssueTitle();

    Issue first = getClient().parseIssueFromResponse(createAndTrack(sharedTitle, null));
    Issue second = getClient().parseIssueFromResponse(createAndTrack(sharedTitle, null));

    assertThat(second.getTitle())
        .as("Duplicate title should be accepted verbatim, not de-duplicated or suffixed")
        .isEqualTo(first.getTitle())
        .isEqualTo(sharedTitle);
    assertThat(second.getId())
        .as("Each issue must receive its own global id")
        .isNotEqualTo(first.getId());
    assertThat(second.getIid())
        .as("Each issue must receive its own project-scoped iid")
        .isNotEqualTo(first.getIid());
  }

  @Test
  @DisplayName("TC005: A previously unseen label is created on the project")
  @Description(
      "Applying a label that does not exist creates it as a project label, a documented "
          + "side effect that reaches beyond the issue being created. Worth pinning because it is easy "
          + "to overlook: a typo in an automation script silently pollutes the project's label list, "
          + "and there is no error to indicate it happened.")
  void testUnknownLabelIsCreatedOnTheProject() {
    String newLabel = TestDataGenerator.generateLabel();

    Response response =
        createAndTrack(
            getClient()
                .createIssue(
                    TestDataGenerator.generateIssueTitle(), null, List.of(newLabel), null, null));

    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("A label that did not previously exist must be created and applied")
        .containsExactly(newLabel);

    Response listed = getClient().listIssues(null, newLabel, null);
    ApiAssertions.assertStatusCodeOk(listed);
    ApiAssertions.assertListSize(listed, "$", 1);
  }

  @Test
  @DisplayName("TC006: Omitting the title is rejected")
  @Description(
      "Distinct from TC007's empty string: the parameter is absent rather than blank. "
          + "A validator checking only for emptiness would accept a missing key and create an "
          + "untitled issue, so both forms need covering.")
  void testMissingTitleIsRejected() {
    Response response = getClient().createIssue(Map.of("description", "no title supplied"));

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertResponseContains(response, "title");
  }

  // ---------------------------------------------------------------------------------
  // Boundary conditions
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC007: Create issue with title at the 255-character limit")
  @Description(
      "Upper boundary, valid side. A title of exactly 255 characters is within the "
          + "documented limit and must be stored in full. The returned length is asserted explicitly "
          + "because silent truncation at the boundary is data loss the caller cannot detect.")
  void testCreateIssueWithTitleAtMaxLength() {
    String title = TestDataGenerator.generateTitleOfLength(MAX_TITLE_LENGTH);

    Response response = createAndTrack(title, null);

    assertThat(getClient().parseIssueFromResponse(response).getTitle())
        .as("Title at the maximum length must be stored without truncation")
        .hasSize(MAX_TITLE_LENGTH)
        .isEqualTo(title);
  }

  @Test
  @DisplayName("TC008: Create issue with title one character over the limit")
  @Description(
      "Upper boundary, invalid side. A 256-character title exceeds the limit and must be "
          + "rejected with 400 naming the offending field. The failure mode that matters is silent "
          + "truncation to 255 with a 201 — the caller would believe the full title was saved.")
  void testCreateIssueWithTitleExceedingMaxLength() {
    String title = TestDataGenerator.generateTitleOfLength(MAX_TITLE_LENGTH + 1);

    Response response = getClient().createIssue(title, null);

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertResponseContains(response, "title");
  }

  // ---------------------------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC009: Create issue with empty title")
  @Description(
      "Title is the sole mandatory field, so an empty value violates the required-field "
          + "constraint and must be rejected with 400 rather than creating an untitled issue.")
  void testCreateIssueWithEmptyTitle() {
    Response response = getClient().createIssue("", null);

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertResponseContains(response, "title");
  }

  @Test
  @DisplayName("TC010: Create issue with whitespace-only title")
  @Description(
      "GitLab trims leading and trailing whitespace before validating that a title is "
          + "present, so spaces and tabs reduce to empty and must be rejected exactly as TC007 is. "
          + "Accepting it would create an issue with a blank title that no search could find.")
  void testCreateIssueWithWhitespaceOnlyTitle() {
    Response response = getClient().createIssue("   \t  ", null);

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertResponseContains(response, "title");
  }

  @Test
  @DisplayName("TC011: Create issue with a full payload")
  @Description(
      "Every self-sufficient optional parameter supplied in a single request. The "
          + "field-specific tests above each verify one parameter in isolation, which would not catch "
          + "an endpoint that honours only the first recognised field or drops one when several are "
          + "present. issue_type is set to a non-default value deliberately: asserting 'issue' would "
          + "pass even if the parameter were ignored entirely. Premium-only parameters "
          + "(assignee_ids, epic_id, weight) and those requiring pre-provisioned entities "
          + "(assignee_id, milestone) are excluded — see README section 10.")
  void testCreateIssueWithFullPayload() {
    String title = TestDataGenerator.generateIssueTitle();
    String description = TestDataGenerator.generateIssueDescription();
    List<String> labels = TestDataGenerator.generateLabels(2);
    String dueDate = LocalDate.now().plusDays(30).toString();

    Response response =
        createAndTrack(
            getClient()
                .createIssue(
                    Map.of(
                        "title",
                        title,
                        "description",
                        description,
                        "labels",
                        String.join(",", labels),
                        "due_date",
                        dueDate,
                        "confidential",
                        true,
                        "issue_type",
                        "task")));

    ApiAssertions.assertFieldEquals(response, "title", title);
    ApiAssertions.assertFieldEquals(response, "description", description);
    ApiAssertions.assertFieldEquals(response, "due_date", dueDate);
    ApiAssertions.assertFieldEquals(response, "confidential", true);
    ApiAssertions.assertFieldEquals(response, "issue_type", "task");
    ApiAssertions.assertFieldEquals(response, "state", "opened");
    ApiAssertions.assertFieldsNotNull(response, "id", "iid", "created_at", "web_url");

    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("Labels must still be applied when supplied alongside other parameters")
        .containsExactlyInAnyOrderElementsOf(labels);
  }

  @Test
  @DisplayName("TC012: Create issue with a description at the documented limit")
  @Description(
      "The description limit is 1,048,576 characters. TC406 in the edge-case suite "
          + "exercises 100,000, which sits comfortably inside and therefore says nothing about the "
          + "boundary. Storing the full value matters because descriptions carry stack traces and log "
          + "excerpts, and truncation at the limit is data loss the caller cannot detect.")
  void testCreateIssueWithDescriptionAtMaxLength() {
    String description = "x".repeat(MAX_DESCRIPTION_LENGTH);

    Response response = createAndTrack(TestDataGenerator.generateIssueTitle(), description);

    assertThat(getClient().parseIssueFromResponse(response).getDescription())
        .as("A description at the limit must be stored without truncation")
        .hasSize(MAX_DESCRIPTION_LENGTH);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  /**
   * Creates an issue and registers it for teardown, returning the raw response.
   *
   * <p>{@code BaseTest.createTrackedIssue} returns a parsed {@link Issue}, which discards the
   * status code and headers these tests assert on.
   *
   * @param title Issue title
   * @param description Issue description, may be null
   * @return the creation response, asserted to be 201
   */
  private Response createAndTrack(String title, String description) {
    return createAndTrack(getClient().createIssue(title, description));
  }

  /**
   * Asserts a creation succeeded and registers the new issue for teardown.
   *
   * @param response Response from any create call
   * @return the same response, for chaining into assertions
   */
  private Response createAndTrack(Response response) {
    ApiAssertions.assertStatusCodeCreated(response);
    trackIssue(getClient().parseIssueFromResponse(response).getIid());
    return response;
  }
}
