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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * DELETE operation tests for the GitLab Issues API.
 *
 * <p>Endpoint under test: {@code DELETE /projects/:id/issues/:issue_iid}
 *
 * <p>Permissions: deletion requires the Owner role on the project, or instance Admin. A token held
 * by a Maintainer or below fails every test here with 403/404 — a configuration problem rather than
 * a product defect. See the README prerequisites.
 *
 * <p>Issues are registered with {@link BaseTest} and removed in teardown. Cleanup tolerates a 404,
 * the normal outcome for the many tests that delete their own issue as the behaviour under test.
 */
@Feature("GitLab Issues API - DELETE")
@Tag("DELETE")
class IssueDeleteTests extends BaseTest {

  /** An iid far beyond anything the test project will generate. */
  private static final long NON_EXISTENT_IID = 999_999L;

  /** A project id far beyond any real project on the instance. */
  private static final String NON_EXISTENT_PROJECT_ID = "999999999";

  // ---------------------------------------------------------------------------------
  // Happy path
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC013: Delete an existing issue")
  @Description(
      "A successful delete must return 204 No Content with an empty body. The status is "
          + "asserted exactly rather than 'any 2xx': a 200 carrying a body would mean the endpoint "
          + "returns the deleted resource, a different contract that would break clients not expecting "
          + "a payload.")
  @Tag("Smoke")
  void testDeleteIssue() {
    Issue issue = createTrackedIssue();

    Response response = getClient().deleteIssue(issue.getIid());

    ApiAssertions.assertStatusCodeNoContent(response);
    ApiAssertions.assertBodyEmpty(response);
  }

  @Test
  @DisplayName("TC014: Deleted issue cannot be retrieved")
  @Description(
      "Verifies the delete removed the resource rather than merely reporting success. A "
          + "subsequent GET must return 404 — a soft delete that left the issue readable would pass "
          + "TC301 but fail here.")
  void testDeletedIssueCannotBeRetrieved() {
    Issue issue = createTrackedIssue();

    deleteSuccessfully(issue.getIid());

    ApiAssertions.assertStatusCodeNotFound(getClient().getIssue(issue.getIid()));
  }

  @Test
  @DisplayName("TC015: Deleted issue is absent from the project issue list")
  @Description(
      "Complements TC302 by checking the list projection rather than direct retrieval. "
          + "Filtering by exact iid via 'iids[]' keeps this deterministic: that filter is an indexed "
          + "exact match and immediately consistent, whereas full-text search is not guaranteed "
          + "read-your-writes and would make the assertion flaky.")
  void testDeletedIssueAbsentFromList() {
    Issue issue = createTrackedIssue();
    deleteSuccessfully(issue.getIid());

    Response response = getClient().listIssuesByIid(issue.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSize(response, "$", 0);
  }

  @Test
  @DisplayName("TC016: Delete a closed issue")
  @Description(
      "Deletion must be independent of state. Closing is a lifecycle transition and "
          + "deletion is removal, so a closed issue remains deletable — a 4xx here would mean the two "
          + "concepts had been conflated.")
  void testDeleteClosedIssue() {
    Issue issue = createTrackedIssue();
    ApiAssertions.assertStatusCodeOk(getClient().closeIssue(issue.getIid()));

    deleteSuccessfully(issue.getIid());

    ApiAssertions.assertStatusCodeNotFound(getClient().getIssue(issue.getIid()));
  }

  @Test
  @DisplayName("TC017: Delete an issue carrying labels and a description")
  @Description(
      "A fully populated issue has label links and other dependent records. Deleting it "
          + "must succeed cleanly rather than failing on a constraint, exercising a cascade path a "
          + "title-only issue never reaches.")
  void testDeleteIssueWithAssociatedData() {
    List<String> labels = TestDataGenerator.generateLabels(3);
    Response createResponse =
        getClient()
            .createIssue(
                TestDataGenerator.generateIssueTitle(),
                TestDataGenerator.generateIssueDescription(),
                labels,
                null,
                null);
    ApiAssertions.assertStatusCodeCreated(createResponse);
    Issue issue = getClient().parseIssueFromResponse(createResponse);
    trackIssue(issue.getIid());

    deleteSuccessfully(issue.getIid());

    ApiAssertions.assertStatusCodeNotFound(getClient().getIssue(issue.getIid()));
  }

  // ---------------------------------------------------------------------------------
  // Contract behaviour
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC018: Deleting the same issue twice returns 404 on the second call")
  @Description(
      "DELETE is idempotent in effect but not in status code: the resource is gone either "
          + "way, yet only the first call finds something to delete. Pinning the second response to 404 "
          + "documents that distinction — a 204 on repeat would be defensible REST design but is not "
          + "what GitLab does, and clients using the 404 to detect a lost race would break silently if "
          + "it changed.")
  void testDeleteIssueTwice() {
    Issue issue = createTrackedIssue();
    deleteSuccessfully(issue.getIid());

    Response response = getClient().deleteIssue(issue.getIid());

    ApiAssertions.assertStatusCodeNotFound(response);
  }

  @Test
  @DisplayName("TC019: A deleted iid is never reassigned")
  @Description(
      "The project-scoped iid counter is monotonic and does not roll back on deletion. "
          + "Recycling an iid would be a serious defect: bookmarks, external tickets and audit records "
          + "referencing the deleted issue would silently resolve to an unrelated one.")
  void testDeletedIidIsNotReused() {
    Long deletedIid = createTrackedIssue().getIid();
    deleteSuccessfully(deletedIid);

    Issue recreated = createTrackedIssue();

    assertThat(recreated.getIid())
        .as("A new issue must receive a fresh iid, never the one just freed")
        .isGreaterThan(deletedIid);
  }

  @Test
  @DisplayName("TC020: Deleting an issue frees its title for reuse")
  @Description(
      "Titles are neither unique nor reserved after deletion. Recreating an issue with the "
          + "title of a deleted one must succeed, confirming deletion releases any incidental "
          + "constraint and leaves no tombstone blocking the value.")
  void testTitleIsReusableAfterDeletion() {
    String title = TestDataGenerator.generateIssueTitle();
    Issue original = createTrackedIssue(title, null);
    deleteSuccessfully(original.getIid());

    Response response = getClient().createIssue(title, null);

    ApiAssertions.assertStatusCodeCreated(response);
    trackIssue(getClient().parseIssueFromResponse(response).getIid());
    ApiAssertions.assertFieldEquals(response, "title", title);
  }

  // ---------------------------------------------------------------------------------
  // Negative and edge cases
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC021: Delete a non-existent issue")
  @Description(
      "An iid that has never existed must return 404 with a JSON error payload. The "
          + "content type is asserted so clients can parse errors with the same deserialiser they use "
          + "for success responses.")
  void testDeleteNonExistentIssue() {
    Response response = getClient().deleteIssue(NON_EXISTENT_IID);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC022: Delete with iid zero")
  @Description(
      "Zero lies outside the valid range — iids start at 1. A classic off-by-one probe: an "
          + "implementation treating the identifier as a falsy or default value could mis-route the "
          + "request rather than rejecting it.")
  void testDeleteWithZeroIid() {
    Response response = getClient().deleteIssue(0L);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC023: Delete with a negative iid")
  @Description(
      "A negative identifier can never match a record. The assertion that matters is the "
          + "absence of a 5xx: an unhandled cast or unguarded query on this input would surface as a "
          + "server error, which is the defect being hunted. The exact 4xx is left open because "
          + "rejection may occur at routing or at parameter coercion.")
  void testDeleteWithNegativeIid() {
    Response response = getClient().deleteIssue(-1L);

    ApiAssertions.assertClientError(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC024: Delete an issue without authentication")
  @Description(
      "An unauthenticated destructive call must be refused at the authentication layer "
          + "with 401, before any lookup occurs. The issue's survival is verified afterwards, proving "
          + "the request was genuinely rejected rather than acted on and then reported as an error.")
  void testDeleteIssueWithoutAuthentication() {
    Issue issue = createTrackedIssue();

    Response response = getClient().deleteIssueWithoutAuth(issue.getIid());

    assertRefusedAndIssueSurvives(response, issue.getIid());
  }

  @Test
  @DisplayName("TC025: Delete an issue with an invalid token")
  @Description(
      "Distinct from TC312: credentials are supplied but invalid. This must also yield 401 "
          + "rather than 403 — the request is unauthenticated, not merely unauthorised — and the issue "
          + "must survive.")
  void testDeleteIssueWithInvalidToken() {
    Issue issue = createTrackedIssue();

    Response response = getClient().deleteIssueWithToken(issue.getIid(), "invalid-token-value");

    assertRefusedAndIssueSurvives(response, issue.getIid());
  }

  @Test
  @DisplayName("TC026: Delete an issue from a non-existent project")
  @Description(
      "With an unknown project in the path the request must fail at project resolution and "
          + "never reach issue lookup. Confirms project scope is enforced and that an iid cannot be "
          + "deleted out of its project context.")
  void testDeleteIssueFromNonExistentProject() {
    Response response = getClient().deleteIssueInProject(NON_EXISTENT_PROJECT_ID, NON_EXISTENT_IID);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  /**
   * Deletes an issue as a precondition, asserting the delete itself succeeded.
   *
   * <p>Used where the deletion is setup rather than the behaviour under test, so a failure here
   * reports the real cause instead of surfacing as a confusing assertion further down.
   *
   * @param issueIid Project-scoped issue iid
   */
  private void deleteSuccessfully(Long issueIid) {
    ApiAssertions.assertStatusCodeNoContent(getClient().deleteIssue(issueIid));
  }

  /**
   * Asserts a destructive call was refused at the authentication layer and had no effect.
   *
   * <p>The follow-up read is the substantive half: a 401 alone proves only what the API reported,
   * not what it did.
   *
   * @param response Response from the rejected call
   * @param issueIid Issue that must still exist
   */
  private void assertRefusedAndIssueSurvives(Response response, Long issueIid) {
    ApiAssertions.assertStatusCodeUnauthorized(response);
    ApiAssertions.assertStatusCodeOk(getClient().getIssue(issueIid));
  }
}
