package com.abnamro.assignment.provider;

import com.abnamro.assignment.dto.IssueRequestDto;
import com.abnamro.assignment.model.Issue;
import com.github.javafaker.Faker;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test Data Provider using JavaFaker. Generates realistic test data for GitLab Issues API testing.
 * Supports multiple locales and customizable data generation.
 */
public class TestDataProvider {

  private static final Logger logger = LoggerFactory.getLogger(TestDataProvider.class);
  private static final Faker faker = new Faker();
  private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private TestDataProvider() {
    // Utility class - prevent instantiation
  }

  // ============ Issue Data Generation ============

  /** Generate a random issue title */
  public static String generateIssueTitle() {
    return faker.lorem().sentence(5, 10);
  }

  /** Generate a random issue description */
  public static String generateIssueDescription() {
    return faker.lorem().paragraphs(2).stream().reduce("", (a, b) -> a + "\n" + b).trim();
  }

  /** Generate multiple random issue titles */
  public static List<String> generateIssueTitles(int count) {
    return IntStream.range(0, count).mapToObj(i -> generateIssueTitle()).toList();
  }

  /** Generate a random issue request DTO */
  public static IssueRequestDto generateIssueRequestDto() {
    return IssueRequestDto.builder()
        .title(generateIssueTitle())
        .description(generateIssueDescription())
        .labels(generateLabels(faker.random().nextInt(1, 4)))
        .state("opened")
        .build();
  }

  /** Generate multiple issue request DTOs */
  public static List<IssueRequestDto> generateIssueRequestDtos(int count) {
    return IntStream.range(0, count).mapToObj(i -> generateIssueRequestDto()).toList();
  }

  /** Generate an Issue model with fake data */
  public static Issue generateIssueModel() {
    Issue issue = new Issue();
    issue.setTitle(generateIssueTitle());
    issue.setDescription(generateIssueDescription());
    issue.setState("opened");
    issue.setLabels(generateLabels(faker.random().nextInt(4) + 1));
    issue.setCreatedAt(faker.date().toString());
    return issue;
  }

  // ============ Label Generation ============

  /** Generate a single random label */
  public static String generateLabel() {
    String[] labelPrefixes = {
      "bug", "feature", "enhancement", "documentation", "refactor", "test", "security"
    };
    String prefix = labelPrefixes[faker.random().nextInt(labelPrefixes.length)];
    String suffix = faker.lorem().word();
    return (prefix + "-" + suffix).toLowerCase();
  }

  /** Generate multiple random labels */
  public static List<String> generateLabels(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> generateLabel())
        .distinct()
        .limit(count)
        .toList();
  }

  /** Generate common predefined labels */
  public static List<String> generatePredefinedLabels() {
    return List.of(
        "bug", "enhancement", "documentation", "urgent", "low-priority", "in-progress", "blocked");
  }

  // ============ User Data Generation ============

  /** Generate random username */
  public static String generateUsername() {
    return faker.name().username();
  }

  /** Generate random email */
  public static String generateEmail() {
    return faker.internet().emailAddress();
  }

  /** Generate random user ID */
  public static Long generateUserId() {
    return faker.random().nextLong();
  }

  /** Generate random user name */
  public static String generateUserName() {
    return faker.name().fullName();
  }

  // ============ Date/Time Generation ============

  /** Generate a future date as string (yyyy-MM-dd) */
  public static String generateFutureDate() {
    return generateFutureDate(1, 365);
  }

  /** Generate a future date within specified range */
  public static String generateFutureDate(int minDays, int maxDays) {
    int daysInFuture = faker.random().nextInt(minDays, maxDays);
    return LocalDate.now().plusDays(daysInFuture).format(dateFormatter);
  }

  /** Generate a past date as string (yyyy-MM-dd) */
  public static String generatePastDate() {
    return generatePastDate(1, 365);
  }

  /** Generate a past date within specified range */
  public static String generatePastDate(int minDays, int maxDays) {
    int daysInPast = faker.random().nextInt(minDays, maxDays);
    return LocalDate.now().minusDays(daysInPast).format(dateFormatter);
  }

  /** Generate a date between two dates */
  public static String generateDateBetween(LocalDate start, LocalDate end) {
    long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
    long randomDays = faker.random().nextLong();
    return start.plusDays(Math.abs(randomDays) % (days + 1)).format(dateFormatter);
  }

  // ============ Text/String Generation ============

  /** Generate random sentence */
  public static String generateSentence() {
    return faker.lorem().sentence();
  }

  /** Generate random paragraph */
  public static String generateParagraph() {
    return faker.lorem().paragraph();
  }

  /** Generate random word */
  public static String generateWord() {
    return faker.lorem().word();
  }

  /** Generate random alphanumeric string */
  public static String generateAlphanumeric(int length) {
    return faker.lorem().characters(length, true);
  }

  /** Generate random numeric string */
  public static String generateNumeric(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append(faker.random().nextInt(10));
    }
    return sb.toString();
  }

  // ============ Special Characters & Edge Cases ============

  /** Generate string with special characters */
  public static String generateWithSpecialCharacters() {
    String baseString = faker.lorem().sentence(3, 5);
    String specialChars = "~!@#$%^&*()_+-=[]{}|;:',.<>?/";
    String random = specialChars.charAt(faker.random().nextInt(specialChars.length())) + "";
    return baseString.substring(0, baseString.length() - 1) + random;
  }

  /** Generate unicode string with emojis and special characters */
  public static String generateUnicodeString() {
    return faker.lorem().words(3).stream().reduce("", (a, b) -> a + " " + b) + " 🚀 ✅ 🎯";
  }

  /** Generate very long string */
  public static String generateLongString(int length) {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < length) {
      sb.append(faker.lorem().word()).append(" ");
    }
    return sb.toString().substring(0, length);
  }

  /** Generate HTML string */
  public static String generateHtmlContent() {
    return "<b>" + faker.lorem().sentence(3) + "</b><br/><i>" + faker.lorem().sentence(3) + "</i>";
  }

  /** Generate JSON string */
  public static String generateJsonContent() {
    return String.format(
        "{\"key\": \"%s\", \"value\": \"%s\", \"timestamp\": \"%s\"}",
        faker.lorem().word(), faker.lorem().word(), System.currentTimeMillis());
  }

  // ============ Number Generation ============

  /** Generate random integer */
  public static int generateRandomInt(int min, int max) {
    return faker.random().nextInt(max - min) + min;
  }

  /** Generate random long */
  public static long generateRandomLong() {
    return faker.random().nextLong();
  }

  /** Generate random boolean */
  public static boolean generateRandomBoolean() {
    return faker.bool().bool();
  }

  // ============ Collection Generation ============

  /** Generate random list of strings */
  public static List<String> generateStringList(int size) {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(faker.lorem().word());
    }
    return list;
  }

  /** Generate random priority level */
  public static String generatePriority() {
    return faker.options().option("low", "medium", "high", "urgent");
  }

  /** Generate random state */
  public static String generateIssueState() {
    return faker.options().option("opened", "closed");
  }

  /** Generate random milestone name */
  public static String generateMilestoneName() {
    return "v" + faker.random().nextInt(1, 10) + "." + faker.random().nextInt(0, 99);
  }

  // ============ Logging Utilities ============

  /** Log generated data */
  public static void logGeneratedData(String dataType, Object value) {
    logger.debug("Generated {} - {} : {}", dataType, value.getClass().getSimpleName(), value);
  }
}
