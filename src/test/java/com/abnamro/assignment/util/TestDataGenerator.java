package com.abnamro.assignment.util;

import com.github.javafaker.Faker;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Generates test data for issue creation.
 *
 * <p>Every generated value carries a random suffix. Uniqueness is a correctness requirement, not a
 * convenience: label filter tests assert an exact result count, and title reuse tests assert that
 * two issues are distinct. Faker's vocabulary is small enough that unsuffixed words collide
 * regularly, which would make those tests fail intermittently and for the wrong reason.
 *
 * <p>{@link Faker} is thread-safe for the generators used here, so a single shared instance is
 * sufficient under parallel execution.
 */
public final class TestDataGenerator {

  private static final Faker faker = new Faker();

  /** Length of the random suffix appended to guarantee uniqueness. */
  private static final int SUFFIX_LENGTH = 8;

  private TestDataGenerator() {}

  /**
   * Generate a unique issue title.
   *
   * @return a random sentence with a unique suffix
   */
  public static String generateIssueTitle() {
    return faker.lorem().sentence(5) + " " + uniqueSuffix();
  }

  /**
   * Generate a random issue description.
   *
   * @return a random paragraph
   */
  public static String generateIssueDescription() {
    return faker.lorem().paragraph();
  }

  /**
   * Generate a unique label.
   *
   * <p>Lower-cased and hyphen-joined to match GitLab's label conventions.
   *
   * @return a label guaranteed not to collide with one from another test
   */
  public static String generateLabel() {
    return (faker.lorem().word() + "-" + uniqueSuffix()).toLowerCase();
  }

  /**
   * Generate the requested number of unique labels.
   *
   * <p>Returns exactly {@code count} labels. Each is individually unique, so no de-duplication step
   * is needed — an earlier version filtered with {@code distinct()} after generation and could
   * silently return fewer labels than requested.
   *
   * @param count Number of labels, must be positive
   * @return exactly {@code count} distinct labels
   */
  public static List<String> generateLabels(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive but was " + count);
    }
    return IntStream.range(0, count).mapToObj(i -> generateLabel()).toList();
  }

  /**
   * Generate a unique title of exactly the requested character length.
   *
   * <p>A UUID prefix keeps titles unique across parallel runs; the remainder is padded so the
   * result hits the boundary precisely, which is the whole point of a length test.
   *
   * @param length Exact required length, must be positive
   * @return a title of exactly {@code length} characters
   */
  public static String generateTitleOfLength(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("length must be positive but was " + length);
    }
    String prefix = "boundary-" + UUID.randomUUID() + "-";
    return prefix.length() >= length
        ? prefix.substring(0, length)
        : prefix + "x".repeat(length - prefix.length());
  }

  /** A short random suffix, sufficient to make collisions vanishingly unlikely within a run. */
  private static String uniqueSuffix() {
    return UUID.randomUUID().toString().substring(0, SUFFIX_LENGTH);
  }
}
