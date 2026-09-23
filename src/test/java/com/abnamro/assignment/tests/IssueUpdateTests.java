package com.abnamro.assignment.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.abnamro.assignment.base.BaseTest;
import com.abnamro.assignment.model.Issue;
import com.abnamro.assignment.util.ApiAssertions;
import com.abnamro.assignment.util.TestDataGenerator;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * UPDATE operation tests for the GitLab Issues API.
 *
 * <p>Endpoint under test: {@code PUT /projects/:id/issues/:issue_iid}
 *
 * <p>Two API behaviours shape these tests:
 *
 * <ul>
 *   <li>State changes go through {@code state_event} ({@code close} / {@code reopen}), not the
 *       {@code state} field the API returns. The client maps either form.
 *   <li>{@code labels} <b>replaces</b> the entire set, whereas {@code add_labels} and {@code
 *       remove_labels} mutate it incrementally. Conflating the two is a common and destructive
 *       client bug, so all three paths are covered.
 * </ul>
 *
 * <p>Labels and titles are generated per test rather than hardcoded, so results never depend on
 * execution order or on data left behind by other runs.
 */
@Feature("GitLab Issues API - UPDATE")
@Tag("UPDATE")
class IssueUpdateTests extends BaseTest {

  /** An iid far beyond anything the test project will generate. */
  private static final long NON_EXISTENT_IID = 999_999L;

  // ---------------------------------------------------------------------------------
  // Field updates
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC060: Update issue title")
  @Description(
      "Changing the title must return 200 with the new value and advance updated_at. "
          + "created_at is asserted unchanged: an implementation that recreates rather than mutates the "
          + "record would satisfy a naive title check while destroying the audit trail.")
  @Tag("Smoke")
  void testUpdateIssueTitle() {
    Issue created = createTrackedIssue();
    String newTitle = updatedTitle();

    Response response = getClient().updateIssue(created.getIid(), newTitle, null, null, null);

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertContentTypeJson(response);
    ApiAssertions.assertFieldEquals(response, "title", newTitle);
    ApiAssertions.assertFieldEquals(response, "iid", created.getIid().intValue());
    ApiAssertions.assertFieldEquals(response, "created_at", created.getCreatedAt());
    assertThat(response.jsonPath().getString("updated_at"))
        .as("An update must advance updated_at beyond its value at creation")
        .isNotEqualTo(created.getUpdatedAt());
  }

  @Test
  @DisplayName("TC061: Update issue description")
  @Description(
      "Sets a description on an issue created without one, confirming null-to-value "
          + "transitions work and that a description-only update leaves the title untouched.")
  void testUpdateIssueDescription() {
    String title = TestDataGenerator.generateIssueTitle();
    Issue created = createTrackedIssue(title, null);
    String newDescription = TestDataGenerator.generateIssueDescription();

    Response response = getClient().updateIssue(created.getIid(), null, newDescription, null, null);

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "description", newDescription);
    ApiAssertions.assertFieldEquals(response, "title", title);
  }

  @Test
  @DisplayName("TC062: A partial update leaves unspecified fields intact")
  @Description(
      "The defining risk of PUT-based updates: a request carrying only the title must not "
          + "clear the description or labels. This is the most valuable test in the class — every "
          + "field-specific test would still pass against an API that wiped everything else.")
  void testPartialUpdateDoesNotClobberOtherFields() {
    String description = TestDataGenerator.generateIssueDescription();
    List<String> labels = TestDataGenerator.generateLabels(2);
    Issue created = createLabelledIssue(description, labels);
    String newTitle = updatedTitle();

    Response response = getClient().updateIssue(created.getIid(), newTitle, null, null, null);

    ApiAssertions.assertStatusCodeOk(response);
    Issue updated = getClient().parseIssueFromResponse(response);
    assertThat(updated.getTitle()).as("Title should reflect the update").isEqualTo(newTitle);
    assertThat(updated.getDescription())
        .as("Description was not part of the request and must survive")
        .isEqualTo(description);
    assertThat(updated.getLabels())
        .as("Labels were not part of the request and must survive")
        .containsExactlyInAnyOrderElementsOf(labels);
  }

  @Test
  @DisplayName("TC063: Update multiple fields in a single call")
  @Description(
      "Title, description and labels changed together must all take effect, verifying the "
          + "payload is applied as a whole rather than the server honouring only the first recognised "
          + "parameter.")
  void testUpdateMultipleFields() {
    Issue created = createTrackedIssue();
    String newTitle = updatedTitle();
    String newDescription = TestDataGenerator.generateIssueDescription();
    List<String> newLabels = TestDataGenerator.generateLabels(2);

    Response response =
        getClient().updateIssue(created.getIid(), newTitle, newDescription, newLabels, null);

    ApiAssertions.assertStatusCodeOk(response);
    Issue updated = getClient().parseIssueFromResponse(response);
    assertThat(updated.getTitle()).as("Title should be updated").isEqualTo(newTitle);
    assertThat(updated.getDescription())
        .as("Description should be updated")
        .isEqualTo(newDescription);
    assertThat(updated.getLabels())
        .as("Labels should be updated")
        .containsExactlyInAnyOrderElementsOf(newLabels);
  }

  @Test
  @DisplayName("TC064: Update is persisted, not merely echoed")
  @Description(
      "The update response could in principle reflect the request rather than stored "
          + "state. Re-reading through a separate GET proves the change was committed — without this, "
          + "every other test in the class trusts the write path to report on itself.")
  void testUpdateIsPersisted() {
    Issue created = createTrackedIssue();
    String newTitle = updatedTitle();
    ApiAssertions.assertStatusCodeOk(
        getClient().updateIssue(created.getIid(), newTitle, null, null, null));

    Response response = getClient().getIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "title", newTitle);
  }

  @Test
  @DisplayName("TC065: Unicode and emoji survive an update")
  @Description(
      "Multi-byte characters must round-trip byte-for-byte through the update path. "
          + "Catches encoding faults a pure-ASCII suite never reaches, and the follow-up read confirms "
          + "the value was stored rather than merely echoed.")
  void testUpdateWithUnicodeTitle() {
    Issue created = createTrackedIssue();
    String unicodeTitle = "Déjà vu — 更新済み — 🚀 " + TestDataGenerator.generateIssueTitle();

    Response response = getClient().updateIssue(created.getIid(), unicodeTitle, null, null, null);

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "title", unicodeTitle);
    ApiAssertions.assertFieldEquals(getClient().getIssue(created.getIid()), "title", unicodeTitle);
  }

  // ---------------------------------------------------------------------------------
  // State transitions
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC066: Close an open issue")
  @Description(
      "Closing must set state to closed and populate closed_at, which is null while the "
          + "issue is open and is the only record of when the transition happened. Asserting the "
          + "timestamp alongside the state catches a transition that updates the flag but not the audit "
          + "field.")
  @Tag("Smoke")
  void testCloseIssue() {
    Issue created = createTrackedIssue();

    Response response = getClient().closeIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "state", "closed");
    ApiAssertions.assertFieldsNotNull(response, "closed_at");
  }

  @Test
  @DisplayName("TC067: Reopen a closed issue")
  @Description(
      "Reopening must restore state to opened and clear closed_at. A stale closed_at on an "
          + "open issue would corrupt any reporting that derives cycle time from that field.")
  void testReopenIssue() {
    Issue created = createClosedIssue();

    Response response = getClient().reopenIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "state", "opened");
    ApiAssertions.assertFieldIsNull(response, "closed_at");
  }

  @Test
  @DisplayName("TC068: Closing an already closed issue is a no-op")
  @Description(
      "State transitions are idempotent: re-closing must return 200 and leave the issue "
          + "closed rather than erroring. Clients retrying after a timeout depend on this — a 4xx on "
          + "repeat would turn a successful retry into a spurious failure.")
  void testCloseAlreadyClosedIssue() {
    Issue created = createClosedIssue();

    Response response = getClient().closeIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "state", "closed");
  }

  @Test
  @DisplayName("TC069: Reopening an already open issue is a no-op")
  @Description(
      "The mirror of TC209, covering the reopen branch of the state machine — implemented "
          + "independently of close and therefore able to regress separately.")
  void testReopenAlreadyOpenIssue() {
    Issue created = createTrackedIssue();

    Response response = getClient().reopenIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "state", "opened");
  }

  @Test
  @DisplayName("TC070: A closed issue's fields remain editable")
  @Description(
      "Closing is a state transition, not a lock. Editing the title of a closed issue must "
          + "succeed without silently reopening it — an implementation treating any update as activity "
          + "could plausibly flip the state back.")
  void testUpdateClosedIssue() {
    Issue created = createClosedIssue();
    String newTitle = updatedTitle();

    Response response = getClient().updateIssue(created.getIid(), newTitle, null, null, null);

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "title", newTitle);
    ApiAssertions.assertFieldEquals(response, "state", "closed");
  }

  // ---------------------------------------------------------------------------------
  // Label semantics
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC071: The labels parameter replaces the entire set")
  @Description(
      "Sending 'labels' is wholesale replacement, not a merge. Asserting the original "
          + "labels are gone is the point: a merge implementation would return a superset that a "
          + "'contains the new labels' check would happily accept.")
  void testLabelsParameterReplacesEntireSet() {
    List<String> originalLabels = TestDataGenerator.generateLabels(2);
    Issue created = createLabelledIssue(null, originalLabels);
    List<String> newLabels = TestDataGenerator.generateLabels(2);

    Response response = getClient().updateIssue(created.getIid(), null, null, newLabels, null);

    ApiAssertions.assertStatusCodeOk(response);
    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("labels replaces the set outright; the originals must be gone")
        .containsExactlyInAnyOrderElementsOf(newLabels)
        .doesNotContainAnyElementsOf(originalLabels);
  }

  @Test
  @DisplayName("TC072: add_labels appends without removing existing labels")
  @Description(
      "The incremental counterpart to TC212. Distinguishing the two matters because a "
          + "client reaching for 'labels' when it means 'add_labels' silently destroys labels applied by "
          + "other users — a real and common data-loss bug.")
  void testAddLabelsAppendsToExistingSet() {
    List<String> originalLabels = TestDataGenerator.generateLabels(2);
    Issue created = createLabelledIssue(null, originalLabels);
    String extraLabel = TestDataGenerator.generateLabel();

    Response response = getClient().updateIssue(created.getIid(), Map.of("add_labels", extraLabel));

    ApiAssertions.assertStatusCodeOk(response);
    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("add_labels must preserve existing labels and append the new one")
        .containsAll(originalLabels)
        .contains(extraLabel)
        .hasSize(originalLabels.size() + 1);
  }

  @Test
  @DisplayName("TC073: remove_labels removes only the named labels")
  @Description(
      "Removal must be surgical. Asserting the untouched labels survive guards against an "
          + "implementation that clears the whole set whenever remove_labels is present.")
  void testRemoveLabelsRemovesOnlyNamedLabels() {
    List<String> labels = TestDataGenerator.generateLabels(3);
    Issue created = createLabelledIssue(null, labels);
    String labelToRemove = labels.get(0);

    Response response =
        getClient().updateIssue(created.getIid(), Map.of("remove_labels", labelToRemove));

    ApiAssertions.assertStatusCodeOk(response);
    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("Only the named label should be removed")
        .doesNotContain(labelToRemove)
        .containsExactlyInAnyOrderElementsOf(labels.subList(1, labels.size()));
  }

  @Test
  @DisplayName("TC074 An empty labels value clears all labels")
  @Description(
      "Clearing is expressed as an empty string, not an omitted parameter — omission means "
          + "'leave unchanged'. The distinction is easy to get wrong in a client and impossible to "
          + "detect without an explicit test, since both forms return 200.")
  void testEmptyLabelsClearsAllLabels() {
    Issue created = createLabelledIssue(null, TestDataGenerator.generateLabels(2));

    Response response = getClient().updateIssue(created.getIid(), Map.of("labels", ""));

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSize(response, "labels", 0);
  }

  // ---------------------------------------------------------------------------------
  // due_date handling — observed behaviour
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC075: A non-ISO due_date is coerced rather than rejected")
  @Description(
      "Documents observed behaviour that differs from the documented contract. due_date is "
          + "specified as ISO-8601 (YYYY-MM-DD), but '31-12-2026' is accepted and silently normalised to "
          + "'2026-12-31' rather than refused. Pinned so a future tightening to strict validation is "
          + "detected as a breaking change; TC217 shows why the leniency matters.")
  void testNonIsoDueDateIsCoerced() {
    Issue created = createTrackedIssue();

    Response response = getClient().updateIssue(created.getIid(), Map.of("due_date", "31-12-2026"));

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "due_date", "2026-12-31");
  }

  @Test
  @DisplayName("TC076: An ambiguous non-ISO due_date is resolved day-first without warning")
  @Description(
      "The consequence of the leniency in TC216. '01-02-2026' is valid under both "
          + "DD-MM-YYYY and MM-DD-YYYY conventions; the API resolves it day-first, so a client following "
          + "US convention and intending 2 January silently stores 1 February. No error is returned, "
          + "making the misinterpretation undetectable from the response. Raised as a finding in the "
          + "README: strict ISO-8601 validation would surface the client bug immediately rather than "
          + "corrupting the schedule.")
  void testAmbiguousDueDateIsResolvedDayFirst() {
    Issue created = createTrackedIssue();

    Response response = getClient().updateIssue(created.getIid(), Map.of("due_date", "01-02-2026"));

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "due_date", "2026-02-01");
  }

  // ---------------------------------------------------------------------------------
  // Negative and edge cases
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC077: Update with no parameters is rejected")
  @Description(
      "An update naming nothing to change is meaningless and must be refused with 400 "
          + "rather than returning 200 and bumping updated_at. Silently accepting it would let a buggy "
          + "client churn the audit trail with empty writes.")
  void testUpdateWithNoParametersIsRejected() {
    Issue created = createTrackedIssue();

    Response response = getClient().updateIssue(created.getIid(), Map.of());

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC078: Update title to an empty string is rejected")
  @Description(
      "Title is mandatory at creation and that constraint must hold on update — an API "
          + "permitting it to be cleared lets a valid record become invalid after the fact. The issue is "
          + "re-read to confirm the rejection left the original title intact.")
  void testUpdateTitleToEmptyIsRejected() {
    String originalTitle = TestDataGenerator.generateIssueTitle();
    Issue created = createTrackedIssue(originalTitle, null);

    Response response = getClient().updateIssue(created.getIid(), Map.of("title", ""));

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertFieldEquals(getClient().getIssue(created.getIid()), "title", originalTitle);
  }

  @Test
  @DisplayName("TC079: An unrecognised state_event is rejected")
  @Description(
      "state_event accepts only 'close' and 'reopen'. An arbitrary value must produce a "
          + "400 rather than being ignored: silently discarding an unrecognised transition would leave a "
          + "client believing a state change succeeded when nothing happened.")
  void testInvalidStateEventIsRejected() {
    Issue created = createTrackedIssue();

    Response response = getClient().updateIssue(created.getIid(), Map.of("state_event", "archive"));

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertFieldEquals(getClient().getIssue(created.getIid()), "state", "opened");
  }

  @Test
  @DisplayName("TC080: Update a non-existent issue")
  @Description(
      "An iid that has never existed must return 404 with a JSON error payload, failing at "
          + "lookup before any write is attempted.")
  void testUpdateNonExistentIssue() {
    Response response = getClient().updateIssue(NON_EXISTENT_IID, "Some title", null, null, null);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC081: Update an issue without authentication")
  @Description(
      "An unauthenticated write must be refused at the authentication layer with 401. "
          + "Re-reading the issue proves the request was genuinely rejected rather than applied and then "
          + "reported as an error.")
  void testUpdateIssueWithoutAuthentication() {
    String originalTitle = TestDataGenerator.generateIssueTitle();
    Issue created = createTrackedIssue(originalTitle, null);

    Response response =
        getClient().updateIssueWithoutAuth(created.getIid(), Map.of("title", "Hijacked title"));

    ApiAssertions.assertStatusCodeUnauthorized(response);
    ApiAssertions.assertFieldEquals(getClient().getIssue(created.getIid()), "title", originalTitle);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  /** A distinct title for update assertions, prefixed so intent is visible in failure output. */
  private static String updatedTitle() {
    return "UPDATED: " + TestDataGenerator.generateIssueTitle();
  }

  /**
   * Creates a labelled issue and registers it for teardown.
   *
   * <p>{@code BaseTest.createTrackedIssue} does not accept labels, so this fills the gap without
   * repeating the assert-parse-track sequence at each call site.
   *
   * @param description Issue description, may be null
   * @param labels Labels to apply
   * @return the created issue
   */
  private Issue createLabelledIssue(String description, List<String> labels) {
    Response response =
        getClient()
            .createIssue(TestDataGenerator.generateIssueTitle(), description, labels, null, null);
    ApiAssertions.assertStatusCodeCreated(response);
    Issue issue = getClient().parseIssueFromResponse(response);
    trackIssue(issue.getIid());
    return issue;
  }

  /**
   * Creates an issue and immediately closes it, asserting both steps succeeded.
   *
   * @return the issue, now closed
   */
  private Issue createClosedIssue() {
    Issue issue = createTrackedIssue();
    ApiAssertions.assertStatusCodeOk(getClient().closeIssue(issue.getIid()));
    return issue;
  }
}
