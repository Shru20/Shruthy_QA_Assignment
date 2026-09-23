package com.abnamro.assignment.util;

import com.github.javafaker.Faker;

/**
 * Test data generator using Faker library. Provides utilities to generate random test data for
 * issue creation.
 */
public class TestDataGenerator {

  private static final Faker faker = new Faker();

  private TestDataGenerator() {
    // Private constructor to prevent instantiation
  }

  /**
   * Generate a random issue title.
   *
   * @return Random title
   */
  public static String generateIssueTitle() {
    return faker.lorem().sentence(5);
  }

  /**
   * Generate a random issue description.
   *
   * @return Random description
   */
  public static String generateIssueDescription() {
    return faker.lorem().paragraph();
  }

  /**
   * Generate a random label.
   *
   * @return Random label
   */
  public static String generateLabel() {
    return faker.lorem().word().toLowerCase();
  }

  /**
   * Generate multiple random labels.
   *
   * @param count Number of labels
   * @return List of random labels
   */
  public static java.util.List<String> generateLabels(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> generateLabel())
        .distinct()
        .limit(count)
        .toList();
  }

  /**
   * Generate a random email.
   *
   * @return Random email
   */
  public static String generateEmail() {
    return faker.internet().emailAddress();
  }

  /**
   * Generate a random username.
   *
   * @return Random username
   */
  public static String generateUsername() {
    return faker.name().username();
  }

  /**
   * Generate a future date for due dates.
   *
   * @return Date string in YYYY-MM-DD format
   */
  public static String generateFutureDate() {
    java.util.Date date = faker.date().future(30, java.util.concurrent.TimeUnit.DAYS);
    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
  }

  /**
   * Generate a past date.
   *
   * @return Date string in YYYY-MM-DD format
   */
  public static String generatePastDate() {
    java.util.Date date = faker.date().past(30, java.util.concurrent.TimeUnit.DAYS);
    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
  }
}
