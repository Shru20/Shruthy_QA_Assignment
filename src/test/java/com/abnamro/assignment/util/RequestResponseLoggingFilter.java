package com.abnamro.assignment.util;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs each request and response at DEBUG, with credentials redacted.
 *
 * <p>Redaction is not optional hygiene: CI logs on a public repository are world-readable, and the
 * Allure report is published to GitHub Pages. A filter that printed the raw Authorization header
 * would leak the token to both. Header names are matched case-insensitively because HTTP treats
 * them that way and a client may not send the casing this filter expects.
 *
 * <p>Bodies are truncated. One edge-case test sends a 100,000-character description, which would
 * otherwise bury every other line in the build output.
 */
public class RequestResponseLoggingFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

  private static final Set<String> REDACTED_HEADERS =
      Set.of("private-token", "authorization", "cookie", "set-cookie");

  private static final int MAX_BODY_LENGTH = 2_000;

  @Override
  public Response filter(
      FilterableRequestSpecification requestSpec,
      FilterableResponseSpecification responseSpec,
      FilterContext ctx) {

    if (logger.isDebugEnabled()) {
      logger.debug("--> {} {}", requestSpec.getMethod(), requestSpec.getURI());
      requestSpec
          .getHeaders()
          .forEach(
              header ->
                  logger.debug(
                      "--> {}: {}", header.getName(), mask(header.getName(), header.getValue())));
      if (requestSpec.getBody() != null) {
        logger.debug("--> body: {}", truncate(requestSpec.getBody().toString()));
      }
    }

    long startedAt = System.currentTimeMillis();
    Response response = ctx.next(requestSpec, responseSpec);
    long elapsed = System.currentTimeMillis() - startedAt;

    if (logger.isDebugEnabled()) {
      logger.debug("<-- {} ({} ms)", response.getStatusCode(), elapsed);
      logger.debug("<-- body: {}", truncate(response.getBody().asString()));
    }

    return response;
  }

  /** Replaces the value of any credential-bearing header. */
  private static String mask(String name, String value) {
    return REDACTED_HEADERS.contains(name.toLowerCase()) ? "***redacted***" : value;
  }

  /** Caps body output so a large payload cannot swamp the log. */
  private static String truncate(String body) {
    if (body == null || body.isEmpty()) {
      return "<empty>";
    }
    return body.length() <= MAX_BODY_LENGTH
        ? body
        : body.substring(0, MAX_BODY_LENGTH) + "... [" + body.length() + " chars total]";
  }
}
