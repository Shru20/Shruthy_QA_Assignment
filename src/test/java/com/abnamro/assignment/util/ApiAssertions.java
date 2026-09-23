package com.abnamro.assignment.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assertion utilities for API response validation.
 *
 * <p>Covers status codes, headers and generic JSON checks. Domain-level assertions on the {@link
 * com.abnamro.assignment.model.Issue} model belong in the tests themselves, using AssertJ directly
 * — this class stays transport-focused.
 *
 * <p>Failure messages include the response body wherever it aids diagnosis, so a CI failure is
 * actionable without re-running locally. Logging is at DEBUG: these methods are called many times
 * per test and would otherwise flood pipeline output.
 */
public final class ApiAssertions {

  private static final Logger logger = LoggerFactory.getLogger(ApiAssertions.class);

  private static final int OK = 200;
  private static final int CREATED = 201;
  private static final int NO_CONTENT = 204;
  private static final int BAD_REQUEST = 400;
  private static final int UNAUTHORIZED = 401;
  private static final int FORBIDDEN = 403;
  private static final int NOT_FOUND = 404;
  private static final int CLIENT_ERROR_MIN = 400;
  private static final int CLIENT_ERROR_MAX = 499;

  private ApiAssertions() {}

  // ---------------------------------------------------------------------------------
  // Status codes
  // ---------------------------------------------------------------------------------

  /**
   * Assert that the response status code matches the expected value. The body is included in the
   * failure message so a failing test is diagnosable from the CI log alone.
   *
   * @param response Response object
   * @param expectedCode Expected status code
   */
  public static void assertStatusCode(Response response, int expectedCode) {
    logger.debug("Asserting status code is {}", expectedCode);
    assertThat(response.getStatusCode())
        .as(
            "Expected HTTP %d but got %d. Body: %s",
            expectedCode, response.getStatusCode(), body(response))
        .isEqualTo(expectedCode);
  }

  /** Assert that the response status code is 200 (OK). */
  public static void assertStatusCodeOk(Response response) {
    assertStatusCode(response, OK);
  }

  /** Assert that the response status code is 201 (Created). */
  public static void assertStatusCodeCreated(Response response) {
    assertStatusCode(response, CREATED);
  }

  /**
   * Assert that the response status code is 204 (No Content) — expected from a successful DELETE.
   */
  public static void assertStatusCodeNoContent(Response response) {
    assertStatusCode(response, NO_CONTENT);
  }

  /** Assert that the response status code is 400 (Bad Request). */
  public static void assertStatusCodeBadRequest(Response response) {
    assertStatusCode(response, BAD_REQUEST);
  }

  /**
   * Assert that the response status code is 404 (Not Found).
   *
   * <p>GitLab also returns 404 — not 403 — when a resource exists but is invisible to the caller.
   * That is deliberate, to avoid leaking resource existence, so 404 is the correct expectation in
   * cross-project permission tests.
   */
  public static void assertStatusCodeNotFound(Response response) {
    assertStatusCode(response, NOT_FOUND);
  }

  /**
   * Assert that the response is a client error (4xx).
   *
   * <p>Use only where the exact code is genuinely unspecified. The value here is in what it
   * excludes: a 5xx means unusual input crashed the API rather than being rejected cleanly.
   *
   * @param response Response object
   */
  public static void assertClientError(Response response) {
    logger.debug("Asserting status code is 4xx");
    assertThat(response.getStatusCode())
        .as(
            "Expected a 4xx client error but got %d. Body: %s",
            response.getStatusCode(), body(response))
        .isBetween(CLIENT_ERROR_MIN, CLIENT_ERROR_MAX);
  }

  // ---------------------------------------------------------------------------------
  // Body and JSON
  // ---------------------------------------------------------------------------------

  /**
   * Assert that the response body contains specific text. Used for error payloads, whose envelope
   * shape varies — GitLab returns {@code message} for validation failures and {@code error} for
   * rejected query parameters — so a field-level assertion would be brittle.
   *
   * @param response Response object
   * @param text Text to look for
   */
  public static void assertResponseContains(Response response, String text) {
    logger.debug("Asserting response contains: {}", text);
    assertThat(body(response)).as("Response body should contain: %s", text).contains(text);
  }

  /**
   * Assert that the response body is empty — expected for 204 No Content.
   *
   * @param response Response object
   */
  public static void assertBodyEmpty(Response response) {
    logger.debug("Asserting response body is empty");
    String body = body(response);
    assertThat(body).as("Response body should be empty but was: %s", body).isBlank();
  }

  /**
   * Assert that a JSON field equals the expected value.
   *
   * <p>The value is assigned to an {@code Object} local first: {@code JsonPath.get} is declared
   * {@code <T> T get(String)}, so passing it inline leaves the compiler to infer T from the
   * overloaded {@code assertThat} candidates, which is ambiguous. Note that JSON numbers
   * deserialise to {@code Integer}, so a {@code Long} expected value will not match.
   *
   * @param response Response object
   * @param jsonPath GPath expression, e.g. {@code "title"} or {@code "author.username"}
   * @param expected Expected value
   */
  public static void assertFieldEquals(Response response, String jsonPath, Object expected) {
    logger.debug("Asserting field '{}' equals '{}'", jsonPath, expected);
    Object actual = response.jsonPath().get(jsonPath);
    assertThat(actual)
        .as("JSON field '%s' should equal '%s'", jsonPath, expected)
        .isEqualTo(expected);
  }

  /**
   * Assert that the given JSON fields are all present and non-null. Use to verify server-generated
   * fields on a created resource: {@code id}, {@code iid}, {@code created_at}, {@code web_url}.
   *
   * @param response Response object
   * @param jsonPaths GPath expressions that must all resolve to non-null values
   */
  public static void assertFieldsNotNull(Response response, String... jsonPaths) {
    logger.debug("Asserting fields are non-null: {}", (Object) jsonPaths);
    for (String path : jsonPaths) {
      Object actual = response.jsonPath().get(path);
      assertThat(actual)
          .as("JSON field '%s' should be present and non-null. Body: %s", path, body(response))
          .isNotNull();
    }
  }

  /**
   * Assert that a JSON field is present but explicitly null.
   *
   * <p>Distinct from absent: GitLab returns {@code "closed_at": null} on an open issue rather than
   * omitting the key. This asserts the value, not the key's presence.
   *
   * @param response Response object
   * @param jsonPath GPath expression, e.g. {@code "closed_at"}
   */
  public static void assertFieldIsNull(Response response, String jsonPath) {
    logger.debug("Asserting field '{}' is null", jsonPath);
    Object actual = response.jsonPath().get(jsonPath);
    assertThat(actual).as("JSON field '%s' should be null but was '%s'", jsonPath, actual).isNull();
  }

  /**
   * Assert the exact size of a JSON array. Pass {@code "$"} for a top-level array, which is what
   * GitLab list endpoints return.
   *
   * @param response Response object
   * @param jsonPath GPath expression resolving to a list
   * @param expectedSize Expected element count
   */
  public static void assertListSize(Response response, String jsonPath, int expectedSize) {
    logger.debug("Asserting list '{}' has size {}", jsonPath, expectedSize);
    assertThat(response.jsonPath().getList(jsonPath))
        .as("List '%s' should contain exactly %d elements", jsonPath, expectedSize)
        .hasSize(expectedSize);
  }

  /**
   * Assert that response status code is 401 (Unauthorized).
   *
   * @param response Response object
   */
  public static void assertStatusCodeUnauthorized(Response response) {
    logger.info("Asserting status code is 401");
    assertThat(response.getStatusCode()).as("Response status code should be 401").isEqualTo(401);
  }

  /**
   * Assert that a JSON array holds at most the given number of elements. The key assertion for
   * {@code per_page} boundary tests — GitLab caps page size at 100 regardless of the value sent.
   *
   * @param response Response object
   * @param jsonPath GPath expression resolving to a list
   * @param maxSize Inclusive maximum element count
   */
  public static void assertListSizeAtMost(Response response, String jsonPath, int maxSize) {
    logger.debug("Asserting list '{}' has at most {} elements", jsonPath, maxSize);
    assertThat(response.jsonPath().getList(jsonPath))
        .as("List '%s' should contain at most %d elements", jsonPath, maxSize)
        .hasSizeLessThanOrEqualTo(maxSize);
  }

  // ---------------------------------------------------------------------------------
  // Headers
  // ---------------------------------------------------------------------------------

  /**
   * Assert that the response carries a given header.
   *
   * @param response Response object
   * @param headerName Header name
   */
  public static void assertHeaderExists(Response response, String headerName) {
    logger.debug("Asserting header {} exists", headerName);
    assertThat(response.getHeaders().hasHeaderWithName(headerName))
        .as(
            "Header '%s' should be present. Headers received: %s",
            headerName, response.getHeaders())
        .isTrue();
  }

  /**
   * Assert that a header holds a specific value. Presence is checked first so a missing header
   * fails with a clear message rather than an opaque comparison against null.
   *
   * @param response Response object
   * @param headerName Header name
   * @param headerValue Expected header value
   */
  public static void assertHeader(Response response, String headerName, String headerValue) {
    logger.debug("Asserting header {} equals {}", headerName, headerValue);
    assertHeaderExists(response, headerName);
    assertThat(response.getHeader(headerName))
        .as("Header '%s' should be '%s'", headerName, headerValue)
        .isEqualTo(headerValue);
  }

  /**
   * Assert that the response is JSON. Applied to error responses as well as success paths: a client
   * must be able to parse failures with the same deserialiser it uses for results.
   *
   * @param response Response object
   */
  public static void assertContentTypeJson(Response response) {
    logger.debug("Asserting Content-Type is JSON");
    assertThat(response.getContentType())
        .as("Content-Type should be JSON but was '%s'", response.getContentType())
        .isNotNull()
        .containsIgnoringCase("application/json");
  }

  /**
   * Assert that GitLab's pagination headers are present on a list response. Clients rely on {@code
   * X-Next-Page} to decide whether to keep fetching, so a correct body with missing headers still
   * strands a paginating caller on the first page.
   *
   * @param response Response object
   */
  public static void assertPaginationHeadersPresent(Response response) {
    logger.debug("Asserting pagination headers are present");
    assertHeaderExists(response, "X-Page");
    assertHeaderExists(response, "X-Per-Page");
    assertHeaderExists(response, "X-Next-Page");
  }

  // ---------------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------------

  /** Extracts the body as a string, tolerating empty or unreadable responses such as 204. */
  private static String body(Response response) {
    try {
      return response.getBody() == null ? "" : response.getBody().asString();
    } catch (RuntimeException e) {
      return "<unreadable body>";
    }
  }
}
