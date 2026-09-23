package com.abnamro.assignment.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration Manager for GitLab API Tests. Loads configuration from environment variables
 * (priority) or application.properties file. Supports multiple environments: dev, staging,
 * production
 */
public class TestConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(TestConfiguration.class);
  private static final Properties properties = new Properties();
  private static final String CONFIG_FILE = "application.properties";

  private static final String ENV_PREFIX = "GITLAB_";
  private static final String ENV_VARIANT = "ENV";

  // Configuration Keys
  public static final String API_BASE_URL = "api.base.url";
  public static final String API_TOKEN = "api.token";
  public static final String PROJECT_ID = "api.project.id";
  public static final String REQUEST_TIMEOUT = "request.timeout";
  public static final String CONNECTION_TIMEOUT = "request.connection.timeout";
  public static final String RETRY_COUNT = "request.retry.count";
  public static final String RETRY_WAIT_MS = "request.retry.wait.ms";
  public static final String LOG_LEVEL = "logging.level";
  public static final String ENVIRONMENT = "environment";

  // Default Values
  private static final String DEFAULT_BASE_URL = "https://gitlab.com/api/v4";
  private static final String DEFAULT_TIMEOUT = "10000";
  private static final String DEFAULT_CONN_TIMEOUT = "5000";
  private static final String DEFAULT_RETRY_COUNT = "3";
  private static final String DEFAULT_RETRY_WAIT = "1000";
  private static final String DEFAULT_LOG_LEVEL = "INFO";
  private static final String DEFAULT_ENV = "development";

  static {
    loadProperties();
  }

  /** Load properties from file */
  private static void loadProperties() {
    try (InputStream input =
        TestConfiguration.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
      if (input != null) {
        properties.load(input);
        logger.info("Configuration loaded from {}", CONFIG_FILE);
      } else {
        logger.warn(
            "Configuration file {} not found, using defaults and environment variables",
            CONFIG_FILE);
      }
    } catch (IOException e) {
      logger.error("Error loading configuration from {}: {}", CONFIG_FILE, e.getMessage());
    }
  }

  /** Get configuration value with environment variable priority */
  public static String getProperty(String key) {
    return getProperty(key, null);
  }

  /** Get configuration value with default and environment variable priority */
  public static String getProperty(String key, String defaultValue) {
    // 1. Check environment variables first
    String envKey = ENV_PREFIX + key.toUpperCase().replace(".", "_");
    String envValue = System.getenv(envKey);
    if (envValue != null && !envValue.isEmpty()) {
      logger.debug("Using environment variable: {} = {}", envKey, maskSensitive(envKey, envValue));
      return envValue;
    }

    // 2. Check system properties
    String sysPropValue = System.getProperty(key);
    if (sysPropValue != null && !sysPropValue.isEmpty()) {
      logger.debug("Using system property: {} = {}", key, maskSensitive(key, sysPropValue));
      return sysPropValue;
    }

    // 3. Check properties file
    String propValue = properties.getProperty(key);
    // Support Spring-style placeholders in the properties file:
    //   ${ENV_VAR}            -> resolved from environment (empty if unset)
    //   ${ENV_VAR:default}    -> resolved from environment, falling back to 'default'
    String resolved = resolvePlaceholder(propValue);
    if (resolved != null && !resolved.isEmpty()) {
      logger.debug("Using property file: {} = {}", key, maskSensitive(key, resolved));
      return resolved;
    }

    // 4. Return default or null
    logger.debug("Using default value for key: {}", key);
    return defaultValue;
  }

  /**
   * Resolve a property value that may contain a single placeholder of the form {@code ${ENV_VAR}}
   * or {@code ${ENV_VAR:defaultValue}}. Reads the environment variable at runtime via {@link
   * System#getenv(String)}.
   *
   * @param value raw property value (may be {@code null})
   * @return the resolved value, or {@code null}/empty if it cannot be resolved
   */
  private static String resolvePlaceholder(String value) {
    if (value == null) {
      return null;
    }
    if (!(value.startsWith("${") && value.endsWith("}"))) {
      // Plain literal value, use as-is
      return value;
    }

    String inner = value.substring(2, value.length() - 1); // strip ${ and }
    String envName;
    String fallback = null;
    int sep = inner.indexOf(':');
    if (sep >= 0) {
      envName = inner.substring(0, sep).trim();
      fallback = inner.substring(sep + 1); // keep default as-is (may be empty)
    } else {
      envName = inner.trim();
    }

    String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isEmpty()) {
      return envValue;
    }
    return fallback;
  }

  /** Get configuration value as integer */
  public static int getPropertyAsInt(String key, int defaultValue) {
    try {
      String value = getProperty(key);
      return value != null ? Integer.parseInt(value) : defaultValue;
    } catch (NumberFormatException e) {
      logger.warn("Invalid integer value for key {}, using default", key);
      return defaultValue;
    }
  }

  /** Get configuration value as boolean */
  public static boolean getPropertyAsBoolean(String key, boolean defaultValue) {
    String value = getProperty(key);
    return value != null ? Boolean.parseBoolean(value) : defaultValue;
  }

  /** Mask sensitive configuration values in logs */
  private static String maskSensitive(String key, String value) {
    if (key.toLowerCase().contains("token") || key.toLowerCase().contains("password")) {
      return value.length() > 4 ? value.substring(0, 4) + "***" : "***";
    }
    return value;
  }

  // ============ Public Configuration Methods ============

  public static String getApiBaseUrl() {
    return getProperty(API_BASE_URL, DEFAULT_BASE_URL);
  }

  public static String getApiToken() {
    return getProperty(API_TOKEN, "");
  }

  public static String getProjectId() {
    return getProperty(PROJECT_ID, "");
  }

  public static int getRequestTimeout() {
    return getPropertyAsInt(REQUEST_TIMEOUT, Integer.parseInt(DEFAULT_TIMEOUT));
  }

  public static int getConnectionTimeout() {
    return getPropertyAsInt(CONNECTION_TIMEOUT, Integer.parseInt(DEFAULT_CONN_TIMEOUT));
  }

  public static int getRetryCount() {
    return getPropertyAsInt(RETRY_COUNT, Integer.parseInt(DEFAULT_RETRY_COUNT));
  }

  public static int getRetryWaitMs() {
    return getPropertyAsInt(RETRY_WAIT_MS, Integer.parseInt(DEFAULT_RETRY_WAIT));
  }

  public static String getLogLevel() {
    return getProperty(LOG_LEVEL, DEFAULT_LOG_LEVEL);
  }

  public static String getEnvironment() {
    return getProperty(ENVIRONMENT, DEFAULT_ENV);
  }

  /** Validate critical configuration */
  public static void validateConfiguration() {
    logger.info("=== Configuration Validation ===");

    String token = getApiToken();
    if (token == null || token.isEmpty()) {
      logger.warn("⚠️  GITLAB_TOKEN not configured");
    } else {
      logger.info("✓ GITLAB_TOKEN configured");
    }

    String projectId = getProjectId();
    if (projectId == null || projectId.isEmpty()) {
      logger.warn("⚠️  GITLAB_PROJECT_ID not configured");
    } else {
      logger.info("✓ GITLAB_PROJECT_ID configured");
    }

    logger.info("✓ Base URL: {}", getApiBaseUrl());
    logger.info("✓ Environment: {}", getEnvironment());
    logger.info("✓ Request Timeout: {}ms", getRequestTimeout());
    logger.info("=================================");
  }

  /** Print all configuration */
  public static void printConfiguration() {
    logger.info("=== Test Configuration ===");
    logger.info("Environment: {}", getEnvironment());
    logger.info("Base URL: {}", getApiBaseUrl());
    logger.info("Project ID: {}", getProjectId());
    logger.info("Request Timeout: {}ms", getRequestTimeout());
    logger.info("Connection Timeout: {}ms", getConnectionTimeout());
    logger.info("Retry Count: {}", getRetryCount());
    logger.info("Retry Wait Time: {}ms", getRetryWaitMs());
    logger.info("Log Level: {}", getLogLevel());
    logger.info("==========================");
  }
}
