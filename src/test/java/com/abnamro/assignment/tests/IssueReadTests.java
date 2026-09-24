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
 * READ operation tests for the GitLab Issues API.
 *
 * <p>Endpoints under test:
 *
 * <ul>
 *   <li>{@code GET /projects/:id/issues/:issue_iid} — single issue
 *   <li>{@code GET /projects/:id/issues} — project list, with filtering, sorting and pagination
 * </ul>
 *
 * <p>Filter tests seed their own data before asserting. A filter test that merely iterates whatever
 * happens to be in the project passes vacuously when the list is empty, which is the most common
 * way a read suite gives false confidence.
 *
 * <p>Unauthenticated reads are not covered here: the test project is public, so anonymous access is
 * permitted by design and a 401 assertion would be wrong. The equivalent protection is verified on
 * the write path instead — see TC312 in {@code IssueDeleteTests}.
 */
@Feature("GitLab Issues API - READ")
@Tag("READ")
class IssueReadTests extends BaseTest {

  /** An iid far beyond anything the test project will generate. */
  private static final long NON_EXISTENT_IID = 999_999L;

  /** A project id far beyond any real project on the instance. */
  private static final String NON_EXISTENT_PROJECT_ID = "999999999";

  /** GitLab caps page size at this value regardless of the per_page requested. */
  private static final int MAX_PER_PAGE = 100;

  // ---------------------------------------------------------------------------------
  // Single issue retrieval
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC044: Get issue by IID")
  @Description(
      "Retrieving a known issue must return 200 with the identifiers and title it was "
          + "created with. Confirms the read path resolves the specific record requested rather than "
          + "any issue in the project.")
  @Tag("Smoke")
  void testGetIssueByIid() {
    String title = TestDataGenerator.generateIssueTitle();
    Issue created = createTrackedIssue(title, null);

    Response response = getClient().getIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertContentTypeJson(response);

    Issue fetched = getClient().parseIssueFromResponse(response);
    assertThat(fetched.getIid()).as("Must return the requested iid").isEqualTo(created.getIid());
    assertThat(fetched.getId())
        .as("Global id must match the created issue")
        .isEqualTo(created.getId());
    assertThat(fetched.getTitle()).as("Title must match what was created").isEqualTo(title);
    assertThat(fetched.getState()).as("A newly created issue is open").isEqualTo("opened");
  }

  @Test
  @DisplayName("TC045: Issue response contains all contract fields")
  @Description(
      "Pins the single-issue response contract. Every field asserted is one a client would "
          + "reasonably depend on; silent removal in a future API version is exactly the regression this "
          + "catches. Presence is checked at the JSON level so a field lost in deserialisation cannot be "
          + "confused with one lost from the payload.")
  void testIssueContainsExpectedFields() {
    Issue created = createTrackedIssue();

    Response response = getClient().getIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldsNotNull(
        response,
        "id",
        "iid",
        "project_id",
        "title",
        "state",
        "created_at",
        "updated_at",
        "web_url",
        "author.id",
        "author.username");
    ApiAssertions.assertFieldEquals(response, "state", "opened");
    ApiAssertions.assertFieldEquals(response, "iid", created.getIid().intValue());
  }

  @Test
  @DisplayName("TC046: Retrieved issue reflects the full created payload")
  @Description(
      "A read must return every attribute that was written, not just identifiers. Creates "
          + "an issue with description and labels, then verifies all three round-trip through a separate "
          + "GET — catching a write path that accepts data the read path never exposes.")
  void testRetrievedIssueReflectsCreatedPayload() {
    String title = TestDataGenerator.generateIssueTitle();
    String description = TestDataGenerator.generateIssueDescription();
    List<String> labels = TestDataGenerator.generateLabels(2);
    Issue created = createTrackedIssueWithLabels(title, description, labels);

    Response response = getClient().getIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    Issue fetched = getClient().parseIssueFromResponse(response);
    assertThat(fetched.getTitle()).as("Title should round-trip unchanged").isEqualTo(title);
    assertThat(fetched.getDescription())
        .as("Description should round-trip unchanged")
        .isEqualTo(description);
    assertThat(fetched.getLabels())
        .as("All labels applied at creation should be returned on read")
        .containsExactlyInAnyOrderElementsOf(labels);
  }

  @Test
  @DisplayName("TC047: Get a closed issue")
  @Description(
      "A closed issue stays readable — closing is a state transition, not removal. Also "
          + "asserts closed_at is populated, since it is null while the issue is open and is the only "
          + "record of when the transition occurred.")
  void testGetClosedIssue() {
    Issue created = createTrackedIssue();
    ApiAssertions.assertStatusCodeOk(getClient().closeIssue(created.getIid()));

    Response response = getClient().getIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "state", "closed");
    ApiAssertions.assertFieldsNotNull(response, "closed_at");
  }

  @Test
  @DisplayName("TC048: Reading an issue does not modify it")
  @Description(
      "GET must be safe. Comparing updated_at against the value from creation proves the "
          + "read has no side effects — an endpoint that touched the record would corrupt any client "
          + "relying on that timestamp for change detection or cache invalidation.")
  void testGetDoesNotModifyIssue() {
    Issue created = createTrackedIssue();

    Response response = getClient().getIssue(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertFieldEquals(response, "updated_at", created.getUpdatedAt());
  }

  // ---------------------------------------------------------------------------------
  // Listing and filtering
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC049: List all issues in the project")
  @Description(
      "The list endpoint must return 200 and a JSON array. An issue is seeded first so the "
          + "assertion has teeth: asserting a list is non-null proves nothing, whereas asserting it is "
          + "non-empty after a known write proves the endpoint reads real data.")
  @Tag("Smoke")
  void testListAllIssues() {
    createTrackedIssue();

    Response response = getClient().listIssues();

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertContentTypeJson(response);
    assertThat(getClient().parseIssuesFromResponse(response))
        .as("Project contains at least the issue seeded here")
        .isNotEmpty()
        .allSatisfy(
            issue -> {
              assertThat(issue.getIid()).as("Every listed issue needs an iid").isNotNull();
              assertThat(issue.getState()).as("Every listed issue needs a state").isNotNull();
            });
  }

  @Test
  @DisplayName("TC050: A newly created issue appears in the project list")
  @Description(
      "Verifies the write is visible through the list projection, not only via direct "
          + "retrieval. Filters by exact iid rather than by search: iids[] is an indexed exact match and "
          + "immediately consistent, whereas full-text search is not guaranteed read-your-writes and "
          + "would make this intermittent.")
  void testCreatedIssueAppearsInList() {
    Issue created = createTrackedIssue();

    Response response = getClient().listIssuesByIid(created.getIid());

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSize(response, "$", 1);
    ApiAssertions.assertFieldEquals(response, "[0].iid", created.getIid().intValue());
  }

  @Test
  @DisplayName("TC051: Filter issues by state=opened")
  @Description(
      "Seeds one open and one closed issue so both branches of the filter are exercised, "
          + "then asserts every result is open and the closed one is absent. Without seeding, an empty "
          + "project satisfies the check and the test verifies nothing.")
  void testListIssuesFilteredByOpenedState() {
    Issue openIssue = createTrackedIssue();
    Issue closedIssue = createClosedIssue();

    Response response = getClient().listIssues("opened", null, null);

    ApiAssertions.assertStatusCodeOk(response);
    List<Issue> issues = getClient().parseIssuesFromResponse(response);
    assertThat(issues)
        .as("state=opened must exclude every closed issue")
        .isNotEmpty()
        .allSatisfy(issue -> assertThat(issue.getState()).isEqualTo("opened"));
    assertThat(issues)
        .extracting(Issue::getIid)
        .as("Open issue present, closed issue filtered out")
        .contains(openIssue.getIid())
        .doesNotContain(closedIssue.getIid());
  }

  @Test
  @DisplayName("TC052: Filter issues by state=closed")
  @Description(
      "The mirror of TC108. Asserting both directions matters because a filter that is "
          + "silently ignored returns everything, which would still satisfy a one-sided check on a "
          + "project containing only open issues.")
  void testListIssuesFilteredByClosedState() {
    Issue openIssue = createTrackedIssue();
    Issue closedIssue = createClosedIssue();

    Response response = getClient().listIssues("closed", null, null);

    ApiAssertions.assertStatusCodeOk(response);
    List<Issue> issues = getClient().parseIssuesFromResponse(response);
    assertThat(issues)
        .as("state=closed must exclude every open issue")
        .isNotEmpty()
        .allSatisfy(issue -> assertThat(issue.getState()).isEqualTo("closed"));
    assertThat(issues)
        .extracting(Issue::getIid)
        .as("Closed issue present, open issue filtered out")
        .contains(closedIssue.getIid())
        .doesNotContain(openIssue.getIid());
  }

  @Test
  @DisplayName("TC053: Filter issues by label")
  @Description(
      "Labels are generated uniquely per run, so the expected result set is fully "
          + "determined by this test's own data and cannot be polluted by leftovers from other runs. A "
          + "second unlabelled issue is seeded to prove the filter excludes as well as includes.")
  void testListIssuesFilteredByLabel() {
    String label = TestDataGenerator.generateLabel();
    Issue labelled =
        createTrackedIssueWithLabels(TestDataGenerator.generateIssueTitle(), null, List.of(label));
    createTrackedIssue();

    Response response = getClient().listIssues(null, label, null);

    ApiAssertions.assertStatusCodeOk(response);
    List<Issue> issues = getClient().parseIssuesFromResponse(response);
    assertThat(issues)
        .as("Only the issue carrying the unique label should match")
        .hasSize(1)
        .allSatisfy(issue -> assertThat(issue.getLabels()).contains(label));
    assertThat(issues.get(0).getIid()).isEqualTo(labelled.getIid());
  }

  @Test
  @DisplayName("TC054: A filter matching nothing returns an empty array, not 404")
  @Description(
      "An empty result set is a successful query with no matches, not a missing resource. "
          + "A 404 here would force every client to treat 'no results' as an error condition. Uses a "
          + "freshly generated label that is guaranteed not to exist.")
  void testFilterWithNoMatchesReturnsEmptyList() {
    String unusedLabel = TestDataGenerator.generateLabel();

    Response response = getClient().listIssues(null, unusedLabel, null);

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSize(response, "$", 0);
  }

  // ---------------------------------------------------------------------------------
  // Pagination and sorting
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC055: per_page limits results and sets pagination headers")
  @Description(
      "Requests a single result from a project seeded with two issues. Asserts the body "
          + "size and the pagination headers together: clients rely on X-Next-Page to decide whether to "
          + "keep fetching, so a correct body with missing headers strands them on page one.")
  void testPaginationLimitsResultsAndSetsHeaders() {
    createTrackedIssue();
    createTrackedIssue();

    Response response = getClient().listIssues(Map.of("per_page", 1, "page", 1));

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSize(response, "$", 1);
    ApiAssertions.assertPaginationHeadersPresent(response);
    ApiAssertions.assertHeader(response, "X-Page", "1");
    ApiAssertions.assertHeader(response, "X-Per-Page", "1");
  }

  @Test
  @DisplayName("TC056: Consecutive pages do not overlap")
  @Description(
      "Targets an off-by-one in the offset calculation, which yields a plausible-looking "
          + "page that repeats or skips records. Comparing iids across page one and page two is the only "
          + "way to detect it — each page in isolation looks entirely correct.")
  void testConsecutivePagesDoNotOverlap() {
    createTrackedIssue();
    createTrackedIssue();

    Response firstPage = getClient().listIssues(Map.of("per_page", 1, "page", 1));
    Response secondPage = getClient().listIssues(Map.of("per_page", 1, "page", 2));

    ApiAssertions.assertStatusCodeOk(firstPage);
    ApiAssertions.assertStatusCodeOk(secondPage);
    List<Integer> firstIids = firstPage.jsonPath().getList("iid");
    List<Integer> secondIids = secondPage.jsonPath().getList("iid");
    assertThat(firstIids).as("Page 1 should return one issue").hasSize(1);
    assertThat(secondIids).as("Page 2 should return one issue").hasSize(1);
    assertThat(secondIids)
        .as("Pages must not repeat records")
        .doesNotContainAnyElementsOf(firstIids);
  }

  @Test
  @DisplayName("TC057: per_page above the documented cap is clamped, not honoured")
  @Description(
      "GitLab caps page size at 100. Requesting 200 must return at most 100 rather than "
          + "erroring or honouring the request — an uncapped page size is a denial-of-service vector on "
          + "a large project.")
  void testPerPageAboveMaximumIsCapped() {
    Response response = getClient().listIssues(Map.of("per_page", 200));

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSizeAtMost(response, "$", MAX_PER_PAGE);
  }

  @Test
  @DisplayName("TC058: A page beyond the last returns an empty array")
  @Description(
      "Requesting a page far past the end is a boundary condition, not an error: the query "
          + "is valid and simply matches nothing. Must return 200 with an empty array rather than 404 or "
          + "a server error.")
  void testPageBeyondLastReturnsEmptyList() {
    createTrackedIssue();

    Response response = getClient().listIssues(Map.of("per_page", MAX_PER_PAGE, "page", 10_000));

    ApiAssertions.assertStatusCodeOk(response);
    ApiAssertions.assertListSize(response, "$", 0);
  }

  @Test
  @DisplayName("TC059: Issues are sorted by creation date descending by default")
  @Description(
      "Newest-first is the documented default and the order every UI depends on. Two "
          + "issues are created in sequence and the newer must appear at the lower index. Asserting "
          + "relative position rather than absolute timestamps keeps this robust when other issues exist "
          + "in the project.")
  void testDefaultSortIsNewestFirst() {
    Issue older = createTrackedIssue();
    Issue newer = createTrackedIssue();

    Response response = getClient().listIssues(Map.of("per_page", MAX_PER_PAGE));

    ApiAssertions.assertStatusCodeOk(response);
    List<Integer> iids = response.jsonPath().getList("iid");
    assertThat(iids.indexOf(newer.getIid().intValue()))
        .as("The more recently created issue must sort ahead of the older one")
        .isLessThan(iids.indexOf(older.getIid().intValue()));
  }

  // ---------------------------------------------------------------------------------
  // Negative and edge cases
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC060: Get a non-existent issue")
  @Description(
      "An iid that has never existed must return 404 with a JSON error payload. The "
          + "content type is asserted so clients can parse errors with the same deserialiser they use "
          + "for success responses.")
  void testGetNonExistentIssue() {
    Response response = getClient().getIssue(NON_EXISTENT_IID);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC061: Get with iid zero")
  @Description(
      "Zero lies outside the valid range — iids start at 1. A classic off-by-one probe: an "
          + "implementation treating the identifier as a falsy or default value could mis-route the "
          + "request rather than rejecting it.")
  void testGetWithZeroIid() {
    Response response = getClient().getIssue(0L);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC062: Get with a negative iid")
  @Description(
      "A negative identifier can never match a record. The assertion that matters is the "
          + "absence of a 5xx: an unguarded query or failed cast on this input surfaces as a server "
          + "error, which is the defect being hunted. The exact 4xx is left open because rejection may "
          + "occur at routing or at parameter coercion.")
  void testGetWithNegativeIid() {
    Response response = getClient().getIssue(-1L);

    ApiAssertions.assertClientError(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC063: Get an issue from a non-existent project")
  @Description(
      "With an unknown project in the path the request must fail at project resolution and "
          + "never reach issue lookup. Confirms project scope is genuinely enforced and that an iid "
          + "cannot be read out of its project context.")
  void testGetIssueFromNonExistentProject() {
    Response response = getClient().getIssueInProject(NON_EXISTENT_PROJECT_ID, NON_EXISTENT_IID);

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  /**
   * Creates an issue and immediately closes it, asserting both steps succeeded.
   *
   * @return the issue, now in the closed state
   */
  private Issue createClosedIssue() {
    Issue issue = createTrackedIssue();
    ApiAssertions.assertStatusCodeOk(getClient().closeIssue(issue.getIid()));
    return issue;
  }

  /**
   * Creates a labelled issue and registers it for teardown.
   *
   * <p>{@code BaseTest.createTrackedIssue} does not accept labels, so this fills the gap without
   * repeating the assert-parse-track sequence at each call site.
   *
   * @param title Issue title
   * @param description Issue description, may be null
   * @param labels Labels to apply
   * @return the created issue
   */
  private Issue createTrackedIssueWithLabels(
      String title, String description, List<String> labels) {
    Response response = getClient().createIssue(title, description, labels, null, null);
    ApiAssertions.assertStatusCodeCreated(response);
    Issue issue = getClient().parseIssueFromResponse(response);
    trackIssue(issue.getIid());
    return issue;
  }
}
