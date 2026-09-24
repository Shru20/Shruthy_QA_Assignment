package com.abnamro.assignment.config;

import com.abnamro.assignment.util.RequestResponseLoggingFilter;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.function.Supplier;

/**
 * Central API configuration for GitLab Issues tests.
 *
 * <p>Acts as the single façade over {@link TestConfiguration}: nothing outside this class resolves
 * a setting directly, so the environment-variable-over-properties precedence is defined in exactly
 * one place.
 *
 * <p>Three request specifications are exposed — authenticated, unauthenticated, and one taking an
 * arbitrary token. All three derive from {@link #baseSpecBuilder()} so base URI, content type and
 * any future filters stay identical across positive and negative paths. A negative test must differ
 * from a positive one only in its credentials; anything else and it is testing two things at once.
 */
public final class ApiConfig {

  private static final String TOKEN_ENV_VAR = "GITLAB_API_TOKEN";
  private static final String PROJECT_ID_ENV_VAR = "GITLAB_API_PROJECT_ID";
  private static final String BASE_URL_ENV_VAR = "GITLAB_API_BASE_URL";

  /** Header carrying a personal access token. */
  private static final String PRIVATE_TOKEN_HEADER = "PRIVATE-TOKEN";

  /** Header carrying an OAuth2 bearer token. */
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private static final String BEARER_PREFIX = "Bearer ";

  /** Value of the auth-scheme setting that selects OAuth2 over a personal access token. */
  private static final String OAUTH2_SCHEME = "oauth2";

  private ApiConfig() {}

  /**
   * Base URL of the GitLab API, e.g. {@code https://gitlab.com/api/v4}.
   *
   * @return the configured base URL
   */
  public static String getBaseUrl() {
    return resolve(BASE_URL_ENV_VAR, TestConfiguration::getApiBaseUrl);
  }

  /**
   * Token used for authentication. Interpreted as an OAuth2 token or a personal access token
   * according to {@link #getAuthScheme()}.
   *
   * @return the configured token
   */
  public static String getToken() {
    return resolve(TOKEN_ENV_VAR, TestConfiguration::getApiToken);
  }

  /**
   * Identifier of the GitLab project under test: either a numeric id or a URL-encoded {@code
   * group/project} path.
   *
   * @return the configured project identifier
   */
  public static String getProjectId() {
    return resolve(PROJECT_ID_ENV_VAR, TestConfiguration::getProjectId);
  }

  /**
   * Authentication scheme in use, {@code oauth2} or {@code private-token}.
   *
   * @return the configured scheme
   */
  public static String getAuthScheme() {
    return TestConfiguration.getAuthScheme();
  }

  /**
   * Request specification carrying the configured credentials. Used by every positive-path call.
   *
   * @return an authenticated request specification
   */
  public static RequestSpecification getRequestSpec() {
    return withAuth(baseSpecBuilder(), getToken()).build();
  }

  /**
   * Request specification with no authentication header, for calls that must be rejected at the
   * authentication layer before any resource lookup occurs.
   *
   * @return an unauthenticated request specification
   */
  public static RequestSpecification getNoAuthRequestSpec() {
    return baseSpecBuilder().build();
  }

  /**
   * Request specification carrying a caller-supplied token, for exercising malformed, revoked or
   * under-scoped credentials without mutating the configured token and leaking state into sibling
   * tests.
   *
   * @param token Token to present; may be invalid
   * @return a request specification authenticated with the given token
   */
  public static RequestSpecification getRequestSpecWithToken(String token) {
    return withAuth(baseSpecBuilder(), token).build();
  }

  /**
   * Transport defaults shared by every variant: base URI, content type and accept header. Carries
   * no credentials — callers opt in.
   *
   * @return a builder with transport defaults applied and no authentication
   */
  private static RequestSpecBuilder baseSpecBuilder() {
    return new RequestSpecBuilder()
        .setBaseUri(getBaseUrl())
        .setContentType(ContentType.JSON)
        .setAccept(ContentType.JSON)
        .addFilter(new RequestResponseLoggingFilter());
  }

  /**
   * Applies the configured authentication scheme to a specification builder.
   *
   * <p>GitLab accepts a personal access token via {@code PRIVATE-TOKEN} and an OAuth2 token via
   * {@code Authorization: Bearer}. The scheme is configurable rather than hard-coded so the same
   * suite runs against either credential type without a code change — the assignment specifies
   * OAuth2, while most CI setups issue personal access tokens.
   *
   * @param builder Builder to decorate
   * @param token Token value to present
   * @return the builder with the appropriate auth header applied
   */
  private static RequestSpecBuilder withAuth(RequestSpecBuilder builder, String token) {
    return OAUTH2_SCHEME.equalsIgnoreCase(getAuthScheme())
        ? builder.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
        : builder.addHeader(PRIVATE_TOKEN_HEADER, token);
  }

  /**
   * Resolves a setting from the environment, falling back to the properties file.
   *
   * <p>Environment variables win so CI can inject credentials without a file on disk. A blank value
   * counts as absent: an unset GitHub Actions variable arrives as an empty string rather than null,
   * and treating that as configuration would silently produce an empty base URI or an empty token.
   *
   * @param envVar Environment variable to consult first
   * @param fallback Supplier of the properties-file value
   * @return the resolved value, or whatever the fallback yields when the variable is absent
   */
  private static String resolve(String envVar, Supplier<String> fallback) {
    String fromEnv = System.getenv(envVar);
    return (fromEnv != null && !fromEnv.isBlank()) ? fromEnv : fallback.get();
  }
}
