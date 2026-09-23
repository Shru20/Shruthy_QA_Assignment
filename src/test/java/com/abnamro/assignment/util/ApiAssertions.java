package com.abnamro.assignment.util;

import static org.assertj.core.api.Assertions.*;

import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assertion utilities for API response validation. Provides helper methods for common assertions
 * used in tests.
 */
public class ApiAssertions {

  private static final Logger logger = LoggerFactory.getLogger(ApiAssertions.class);

  private ApiAssertions() {
    // Private constructor to prevent instantiation
  }

  /**
   * Assert that response status code is 200.
   *
   * @param response Response object
   */
  public static void assertStatusCodeOk(Response response) {
    logger.info("Asserting status code is 200");
    assertThat(response.getStatusCode()).as("Response status code should be 200").isEqualTo(200);
  }

  /**
   * Assert that response status code is 201 (Created).
   *
   * @param response Response object
   */
  public static void assertStatusCodeCreated(Response response) {
    logger.info("Asserting status code is 201");
    assertThat(response.getStatusCode()).as("Response status code should be 201").isEqualTo(201);
  }

  /**
   * Assert that response status code matches expected value.
   *
   * @param response Response object
   * @param expectedCode Expected status code
   */
  public static void assertStatusCode(Response response, int expectedCode) {
    logger.info("Asserting status code is {}", expectedCode);
    assertThat(response.getStatusCode())
        .as("Response status code should be %d", expectedCode)
        .isEqualTo(expectedCode);
  }

  /**
   * Assert that response status code is 404 (Not Found).
   *
   * @param response Response object
   */
  public static void assertStatusCodeNotFound(Response response) {
    logger.info("Asserting status code is 404");
    assertThat(response.getStatusCode()).as("Response status code should be 404").isEqualTo(404);
  }

  /**
   * Assert that response status code is 400 (Bad Request).
   *
   * @param response Response object
   */
  public static void assertStatusCodeBadRequest(Response response) {
    logger.info("Asserting status code is 400");
    assertThat(response.getStatusCode()).as("Response status code should be 400").isEqualTo(400);
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
   * Assert that response status code is 403 (Forbidden).
   *
   * @param response Response object
   */
  public static void assertStatusCodeForbidden(Response response) {
    logger.info("Asserting status code is 403");
    assertThat(response.getStatusCode()).as("Response status code should be 403").isEqualTo(403);
  }

  /**
   * Assert that response body contains specific text.
   *
   * @param response Response object
   * @param text Text to look for
   */
  public static void assertResponseContains(Response response, String text) {
    logger.info("Asserting response contains: {}", text);
    assertThat(response.getBody().asString())
        .as("Response body should contain: %s", text)
        .contains(text);
  }

  /**
   * Assert that response body does not contain specific text.
   *
   * @param response Response object
   * @param text Text to check
   */
  public static void assertResponseNotContains(Response response, String text) {
    logger.info("Asserting response does NOT contain: {}", text);
    assertThat(response.getBody().asString())
        .as("Response body should NOT contain: %s", text)
        .doesNotContain(text);
  }

  /**
   * Assert that response has header with specific value.
   *
   * @param response Response object
   * @param headerName Header name
   * @param headerValue Expected header value
   */
  public static void assertHeader(Response response, String headerName, String headerValue) {
    logger.info("Asserting header {} equals {}", headerName, headerValue);
    assertThat(response.getHeader(headerName))
        .as("Header %s should be %s", headerName, headerValue)
        .isEqualTo(headerValue);
  }

  /**
   * Assert that response has specific header.
   *
   * @param response Response object
   * @param headerName Header name
   */
  public static void assertHeaderExists(Response response, String headerName) {
    logger.info("Asserting header {} exists", headerName);
    assertThat(response.getHeaders().hasHeaderWithName(headerName))
        .as("Header %s should exist", headerName)
        .isTrue();
  }
}
