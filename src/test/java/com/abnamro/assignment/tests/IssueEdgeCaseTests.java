package com.abnamro.assignment.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.abnamro.assignment.base.BaseTest;
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
 * Edge case and unusual input tests for the GitLab Issues API.
 *
 * <p>Scope is deliberately narrow: input handling, boundary conditions and malformed requests.
 * Authentication failures, non-existent identifiers and per-operation negative cases live with
 * their respective CRUD suites, where the surrounding context makes them easier to interpret.
 *
 * <p>Several tests pin <b>observed</b> rather than documented behaviour. Where the two differ the
 * description says so explicitly — a test that silently encodes a quirk as if it were the contract
 * is worse than no test at all. TC405, TC407 and TC409 are the cases in question; all three are
 * written up in the README findings section.
 */
@Feature("GitLab Issues API - Edge Cases")
@Tag("EDGE_CASE")
class IssueEdgeCaseTests extends BaseTest {

  /** Exceeds GitLab's 255-character label limit by a wide margin. */
  private static final int OVERSIZED_LABEL_LENGTH = 1_000;

  /** Large but within GitLab's description limit. */
  private static final int LARGE_DESCRIPTION_LENGTH = 100_000;

  // ---------------------------------------------------------------------------------
  // Unusual text input
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC023: Title with leading and trailing whitespace is trimmed")
  @Description(
      "Whitespace around a title is almost always accidental, and storing it verbatim "
          + "produces titles that look identical but do not compare equal, breaking deduplication and "
          + "exact-match search. Asserts the API normalises rather than preserves.")
  void testTitleWhitespaceIsTrimmed() {
    String core = TestDataGenerator.generateIssueTitle();

    Response response = createAndTrack(Map.of("title", "   " + core + "   "));

    ApiAssertions.assertFieldEquals(response, "title", core);
  }

  @Test
  @DisplayName("TC024: Emoji and multi-byte characters survive creation")
  @Description(
      "Titles are user-facing free text and will contain non-Latin scripts and emoji. "
          + "Round-tripping them byte-for-byte proves the whole path — request encoding, storage, "
          + "response encoding — is UTF-8 clean. Mangling here surfaces as mojibake long after the data "
          + "is written, so the value is re-read rather than trusted from the create response alone.")
  void testUnicodeAndEmojiInTitle() {
    String title = "Ünïcödé 日本語 Ελληνικά 🚀🔥 " + TestDataGenerator.generateIssueTitle();

    Response response = createAndTrack(getClient().createIssue(title, null));

    ApiAssertions.assertFieldEquals(response, "title", title);
    assertFieldPersisted(response, "title", title);
  }

  @Test
  @DisplayName("TC025: Script tags in the description are stored verbatim")
  @Description(
      "GitLab renders markdown at display time, so the API must persist input unmodified "
          + "and leave escaping to the renderer. Sanitising on write would corrupt legitimate content — "
          + "code samples in a bug report being the obvious case. The assertion is that the payload is "
          + "unchanged, not that it is neutralised.")
  void testScriptContentInDescriptionIsStoredVerbatim() {
    String description = "<script>alert('xss')</script> and <img src=x onerror=alert(1)>";

    Response response =
        createAndTrack(
            getClient().createIssue(TestDataGenerator.generateIssueTitle(), description));

    ApiAssertions.assertFieldEquals(response, "description", description);
    assertFieldPersisted(response, "description", description);
  }

  @Test
  @DisplayName("TC026: SQL-like input is treated as literal text")
  @Description(
      "A title containing SQL syntax must be stored as data and returned intact. Any "
          + "deviation — an error, a truncated value, an altered string — would indicate the input is "
          + "reaching a query interpreter rather than a parameterised statement. The follow-up list call "
          + "confirms the query path is still healthy afterwards.")
  void testSqlLikeInputIsTreatedAsText() {
    String title = "'; DROP TABLE issues; -- " + TestDataGenerator.generateIssueTitle();

    Response response = createAndTrack(getClient().createIssue(title, null));

    ApiAssertions.assertFieldEquals(response, "title", title);
    ApiAssertions.assertStatusCodeOk(getClient().listIssues());
  }

  @Test
  @DisplayName("TC027: Newlines in a title are stored verbatim")
  @Description(
      "Documents observed behaviour. A title is single-line in every rendering context — "
          + "list rows, page headings, notification subjects — yet embedded newlines are persisted "
          + "rather than stripped or collapsed. Read with TC401, which shows the ends are trimmed, the "
          + "API applies a simple trim rather than full whitespace normalisation. HTML collapses the "
          + "newline so the UI looks correct, but it surfaces in plain-text consumers: CSV exports, "
          + "webhook payloads, terminal output and exact-match search. Pinned so a future tightening is "
          + "detected as a behaviour change.")
  void testNewlinesInTitleArePreserved() {
    String title = "First line\nSecond line " + TestDataGenerator.generateIssueTitle();

    Response response = createAndTrack(Map.of("title", title));

    ApiAssertions.assertFieldEquals(response, "title", title);
    assertFieldPersisted(response, "title", title);
  }

  // ---------------------------------------------------------------------------------
  // Boundary conditions
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC028: A very long description is accepted")
  @Description(
      "Descriptions carry stack traces and logs and are legitimately large. 100,000 "
          + "characters sits within GitLab's documented limit and must be stored in full — silent "
          + "truncation here is data loss the client cannot detect.")
  void testVeryLongDescriptionIsAccepted() {
    String description = "x".repeat(LARGE_DESCRIPTION_LENGTH);

    Response response =
        createAndTrack(
            getClient().createIssue(TestDataGenerator.generateIssueTitle(), description));

    assertThat(getClient().parseIssueFromResponse(response).getDescription())
        .as("A description within the limit must be stored without truncation")
        .hasSize(LARGE_DESCRIPTION_LENGTH);
  }

  @Test
  @DisplayName("TC029: An over-long label is silently discarded, not rejected")
  @Description(
      "Documents observed behaviour materially worse than the alternatives. A "
          + "1,000-character label exceeds GitLab's 255-character limit, yet the request returns 201 "
          + "with an empty labels array: the label is neither applied nor reported as invalid. "
          + "Rejection with 400 would let the client correct the input; truncation would at least "
          + "preserve intent. Silent discard returns success for a partially unfulfilled request, so a "
          + "client labelling issues for routing or SLA tracking loses that categorisation with no "
          + "error to act on. Both the 201 and the empty array are asserted, since the combination is "
          + "the finding.")
  void testOverLongLabelIsSilentlyDiscarded() {
    String longLabel = "A".repeat(OVERSIZED_LABEL_LENGTH);

    Response response =
        createAndTrack(
            getClient()
                .createIssue(
                    TestDataGenerator.generateIssueTitle(), null, List.of(longLabel), null, null));

    ApiAssertions.assertListSize(response, "labels", 0);
    ApiAssertions.assertListSize(reRead(response), "labels", 0);
  }

  @Test
  @DisplayName("TC030: A comma in a label value splits it into separate labels")
  @Description(
      "The labels parameter is comma-delimited, making commas structurally significant and "
          + "therefore impossible to include in a label name. Documents the consequence: a client "
          + "passing one label containing a comma silently creates two. A design constraint of the wire "
          + "format rather than a defect, but invisible from the documentation alone.")
  void testCommaInLabelActsAsSeparator() {
    String first = TestDataGenerator.generateLabel();
    String second = TestDataGenerator.generateLabel();

    Response response =
        createAndTrack(
            Map.of(
                "title", TestDataGenerator.generateIssueTitle(), "labels", first + "," + second));

    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("A comma is a delimiter, so one value becomes two labels")
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  @DisplayName("TC031: An issue can carry a large number of labels")
  @Description(
      "Fifty labels is unusual but legitimate on a heavily triaged issue. All must be "
          + "persisted: a silently applied cap would drop categorisation that downstream automation "
          + "depends on.")
  void testManyLabelsAreAccepted() {
    List<String> labels = TestDataGenerator.generateLabels(50);

    Response response =
        createAndTrack(
            getClient()
                .createIssue(TestDataGenerator.generateIssueTitle(), null, labels, null, null));

    assertThat(getClient().parseIssueFromResponse(response).getLabels())
        .as("Every submitted label must be stored")
        .containsExactlyInAnyOrderElementsOf(labels);
  }

  // ---------------------------------------------------------------------------------
  // Malformed requests
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC032: A syntactically invalid JSON body is rejected")
  @Description(
      "Malformed JSON must fail at parsing with a 4xx, not a 5xx. An unhandled parser "
          + "exception reaching the client as a server error is the specific defect this targets: it "
          + "signals the request pipeline lacks a guard any public API needs.")
  void testMalformedJsonBodyIsRejected() {
    Response response = getClient().createIssueWithRawBody("{\"title\": \"unterminated");

    ApiAssertions.assertClientError(response);
  }

  @Test
  @DisplayName("TC033: A JSON array where an object is expected is rejected")
  @Description(
      "Structurally valid JSON of the wrong shape must be refused. Deserialisers that "
          + "coerce rather than validate can accept an array and silently produce an empty object, "
          + "creating a resource from nothing.")
  void testJsonArrayBodyIsRejected() {
    Response response = getClient().createIssueWithRawBody("[{\"title\": \"array payload\"}]");

    ApiAssertions.assertClientError(response);
  }

  @Test
  @DisplayName("TC034: An empty JSON object is rejected")
  @Description(
      "A create request naming no fields cannot satisfy the mandatory title, so it must "
          + "return 400 rather than creating an untitled issue. Distinct from TC411: the payload is "
          + "well-formed, so the failure must come from validation rather than parsing.")
  void testEmptyJsonObjectIsRejected() {
    Response response = getClient().createIssueWithRawBody("{}");

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC035: An unrecognised issue_type is rejected")
  @Description(
      "issue_type is an enumeration. An arbitrary value must be refused rather than "
          + "defaulting silently: a client believing it created an incident when it created a plain "
          + "issue would miss the alerting attached to that type.")
  void testInvalidIssueTypeIsRejected() {
    Response response =
        getClient()
            .createIssue(
                Map.of("title", TestDataGenerator.generateIssueTitle(), "issue_type", "banana"));

    ApiAssertions.assertClientError(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  @Test
  @DisplayName("TC036: A non-numeric project identifier is rejected")
  @Description(
      "The project path segment accepts a numeric id or a URL-encoded namespace path. A "
          + "value matching neither must resolve to 404 rather than a parsing error, and must certainly "
          + "not fall back to a default project.")
  void testNonNumericProjectIdIsRejected() {
    Response response = getClient().listIssuesInProject("invalid-project-id");

    ApiAssertions.assertStatusCodeNotFound(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  // ---------------------------------------------------------------------------------
  // Pagination boundaries
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC037: per_page=0 is rejected")
  @Description(
      "Zero is not a meaningful page size, and the API refuses it with 400 and an explicit "
          + "message rather than substituting a default or returning an empty page. Either silent "
          + "alternative would be worse: substitution hides the client bug, and an empty page strands a "
          + "paginating loop that never advances. Worth contrasting with TC405 and TC407 — pagination "
          + "parameters are validated strictly while content fields are coerced or dropped, so the "
          + "permissiveness is selective rather than systemic.")
  void testPerPageZeroIsRejected() {
    Response response = getClient().listIssues(Map.of("per_page", 0));

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertContentTypeJson(response);
    ApiAssertions.assertResponseContains(response, "per_page");
  }

  @Test
  @DisplayName("TC038: A non-numeric per_page is rejected")
  @Description(
      "A page size that cannot be parsed as an integer must produce a 400 rather than "
          + "being coerced to zero or to the default. Silent coercion of malformed pagination hides "
          + "client bugs that later surface as missing data.")
  void testNonNumericPerPageIsRejected() {
    Response response = getClient().listIssues(Map.of("per_page", "abc"));

    ApiAssertions.assertStatusCodeBadRequest(response);
    ApiAssertions.assertContentTypeJson(response);
  }

  // ---------------------------------------------------------------------------------
  // Access control attributes
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName("TC039: A confidential issue is created and flagged")
  @Description(
      "Confidentiality is an access-control attribute, so the flag must round-trip "
          + "exactly. An issue created as confidential but returned as public would mean restricted "
          + "content is exposed in list views.")
  void testConfidentialIssueIsFlagged() {
    Response response =
        createAndTrack(
            Map.of("title", TestDataGenerator.generateIssueTitle(), "confidential", true));

    ApiAssertions.assertFieldEquals(response, "confidential", true);
    assertFieldPersisted(response, "confidential", true);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  /**
   * Creates an issue from a parameter map, asserts 201, and registers it for teardown.
   *
   * @param params Parameters to send
   * @return the creation response
   */
  private Response createAndTrack(Map<String, Object> params) {
    return createAndTrack(getClient().createIssue(params));
  }

  /**
   * Asserts a creation succeeded and registers the new issue for teardown.
   *
   * @param response Response from any create call
   * @return the same response, for chaining
   */
  private Response createAndTrack(Response response) {
    ApiAssertions.assertStatusCodeCreated(response);
    trackIssue(getClient().parseIssueFromResponse(response).getIid());
    return response;
  }

  /**
   * Re-reads the issue described by a creation response.
   *
   * <p>Several tests here assert on values the create response already returned. Reading again
   * distinguishes a value genuinely stored from one merely echoed back, which matters most for the
   * encoding and silent-discard cases.
   *
   * @param createResponse Response from a successful create
   * @return the GET response for the same issue
   */
  private Response reRead(Response createResponse) {
    return getClient().getIssue(getClient().parseIssueFromResponse(createResponse).getIid());
  }

  /**
   * Asserts a field holds the expected value when the issue is read back independently.
   *
   * @param createResponse Response from a successful create
   * @param jsonPath Field to check
   * @param expected Expected value
   */
  private void assertFieldPersisted(Response createResponse, String jsonPath, Object expected) {
    ApiAssertions.assertFieldEquals(reRead(createResponse), jsonPath, expected);
  }
}
