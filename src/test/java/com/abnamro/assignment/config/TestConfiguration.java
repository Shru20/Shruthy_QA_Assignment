package com.abnamro.assignment.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves test configuration from, in order of precedence: environment variables, JVM system
 * properties, {@code application.properties}, then a built-in default.
 *
 * <p>Environment variable names are derived from the property key — {@code api.project.id} becomes
 * {@code GITLAB_API_PROJECT_ID} — so a new setting needs no registration beyond its key. CI injects
 * credentials as variables; local runs can use the properties file or {@code -Dapi.token=...}.
 *
 * <p>This class is the only place that reads configuration sources. {@link ApiConfig} is a typed
 * façade over it and must not perform lookups of its own, or the precedence rule would exist in two
 * places and drift.
 */
public final class TestConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(TestConfiguration.class);

  private static final String CONFIG_FILE = "application.properties";
  private static final String ENV_PREFIX = "GITLAB_";

  private static final String API_BASE_URL = "api.base.url";
  private static final String API_TOKEN = "api.token";
  private static final String PROJECT_ID = "api.project.id";
  private static final String AUTH_SCHEME = "api.auth.scheme";

  private static final String DEFAULT_BASE_URL = "https://gitlab.com/api/v4";
  private static final String DEFAULT_AUTH_SCHEME = "private-token";

  private static final Properties properties = new Properties();

  static {
    loadProperties();
  }

  private TestConfiguration() {}

  /**
   * Base URL of the GitLab API. Defaults to gitlab.com; override only for a self-hosted instance.
   *
   * @return the configured base URL, never blank
   */
  public static String getApiBaseUrl() {
    return getProperty(API_BASE_URL, DEFAULT_BASE_URL);
  }

  /**
   * Token used for authentication. Returns an empty string when unconfigured so callers can fail
   * with a targeted message rather than a null check.
   *
   * @return the configured token, or an empty string
   */
  public static String getApiToken() {
    return getProperty(API_TOKEN, "");
  }

  /**
   * Identifier of the project under test: a numeric id or a URL-encoded {@code group/project} path.
   *
   * @return the configured project identifier, or an empty string
   */
  public static String getProjectId() {
    return getProperty(PROJECT_ID, "");
  }

  /**
   * Authentication scheme: {@code oauth2} for a bearer token, {@code private-token} for a personal
   * access token. Defaults to {@code private-token}, which is what most CI setups issue.
   *
   * @return the configured scheme, never blank
   */
  public static String getAuthScheme() {
    return getProperty(AUTH_SCHEME, DEFAULT_AUTH_SCHEME);
  }

  /**
   * Resolves a single setting across all sources.
   *
   * <p>A blank value counts as absent at every layer: an unset GitHub Actions variable arrives as
   * an empty string rather than null, and treating that as configuration would produce an empty
   * base URI or an empty token instead of falling through to the default.
   *
   * @param key Property key, e.g. {@code api.token}
   * @param defaultValue Value to return when no source supplies one
   * @return the resolved value
   */
  private static String getProperty(String key, String defaultValue) {
    String envKey = ENV_PREFIX + key.toUpperCase().replace(".", "_");
    String envValue = System.getenv(envKey);
    if (isPresent(envValue)) {
      logger.debug("Resolved {} from environment variable {}", key, envKey);
      return envValue;
    }

    String systemProperty = System.getProperty(key);
    if (isPresent(systemProperty)) {
      logger.debug("Resolved {} from system property", key);
      return systemProperty;
    }

    String fileValue = properties.getProperty(key);
    if (isPresent(fileValue)) {
      logger.debug("Resolved {} from {}", key, CONFIG_FILE);
      return fileValue;
    }

    logger.debug("Using default for {}", key);
    return defaultValue;
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }

  /**
   * Loads the properties file if present.
   *
   * <p>Absence is not an error: CI supplies everything through environment variables and the file
   * is deliberately absent from version control to keep credentials out of the repository.
   */
  private static void loadProperties() {
    try (InputStream input =
        TestConfiguration.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
      if (input == null) {
        logger.debug("{} not found; using environment variables and defaults", CONFIG_FILE);
        return;
      }
      properties.load(input);
      logger.debug("Loaded configuration from {}", CONFIG_FILE);
    } catch (IOException e) {
      logger.warn("Could not read {}: {}", CONFIG_FILE, e.getMessage());
    }
  }
}
