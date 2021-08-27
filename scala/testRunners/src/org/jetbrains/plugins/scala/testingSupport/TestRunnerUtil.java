package org.jetbrains.plugins.scala.testingSupport;

import org.specs2.execute.Details;
import org.specs2.execute.FailureDetails;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public class TestRunnerUtil {

  public static final PatternWithIndices SPECS_COMPARISON_PATTERN =
    new PatternWithIndices(Pattern.compile("'(.+)' is not equal to '(.*)'", Pattern.MULTILINE | Pattern.DOTALL), 1, 2);
  public static final PatternWithIndices SCALATEST_COMPARISON_PATTERN_WAS =
    new PatternWithIndices(Pattern.compile("(.+) was not equal to (.*)", Pattern.MULTILINE | Pattern.DOTALL), 1, 2);
  public static final PatternWithIndices SCALATEST_COMPARISON_PATTERN_WAS_NULL =
    new PatternWithIndices(Pattern.compile("(.+) was not (null)", Pattern.MULTILINE | Pattern.DOTALL), 1, 2);
  public static final PatternWithIndices SCALATEST_COMPARISON_PATTERN_DID =
    new PatternWithIndices(Pattern.compile("(.+) did not equal (.*)", Pattern.MULTILINE | Pattern.DOTALL), 1, 2);
  public static final PatternWithIndices SCALATEST_PATTERN_SIZE =
    new PatternWithIndices(Pattern.compile("(.+) had size (.+) instead of expected size (.+)", Pattern.MULTILINE | Pattern.DOTALL), 2, 3);
  public static final PatternWithIndices SCALATEST_PATTERN_LENGTH =
    new PatternWithIndices(Pattern.compile("(.+) had length (.+) instead of expected length (.+)", Pattern.MULTILINE | Pattern.DOTALL), 2, 3);
  public static final PatternWithIndices SCALATEST_PATTERN_CLASSINSTANCE =
    new PatternWithIndices(Pattern.compile("(.+) was not an instance of (.+), but an instance of (.+)", Pattern.MULTILINE | Pattern.DOTALL), 3, 2);

  // from ServiceMessage
  private static final String FORMAT_WITHOUT_TZ = "yyyy-MM-dd'T'HH:mm:ss.SSS";
  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(FORMAT_WITHOUT_TZ);

  public static String escapeString(String str) {
    if (str == null) return "";
    return str
        .replaceAll("[|]", "||")
        .replaceAll("[']", "|'")
        .replaceAll("[\n]", "|n")
        .replaceAll("[\r]", "|r")
        .replaceAll("]","|]")
        .replaceAll("\\[","|[");
  }

  public static String formatCurrentTimestamp() {
    Date date = new Date();
    return formatTimestamp(date);
  }

  public static String formatTimestamp(Date date) {
    return TIMESTAMP_FORMAT.format(date);
  }

  public static String actualExpectedAttrs(String actual, String expected) {
    return " expected='" + escapeString(expected) + "' actual='" + escapeString(actual) + "' ";
  }

  public static String actualExpectedAttrsScalaTest(String message) {
    return actualExpectedAttrsFromRegex(message, SCALATEST_COMPARISON_PATTERN_WAS, SCALATEST_COMPARISON_PATTERN_WAS_NULL,
      SCALATEST_COMPARISON_PATTERN_DID, SCALATEST_PATTERN_LENGTH, SCALATEST_PATTERN_SIZE, SCALATEST_PATTERN_CLASSINSTANCE);
  }

  public static String actualExpectedAttrsSpecs2(String message, Details details) {
    String actualExpectedAttrs;
    if (details instanceof FailureDetails) {
      FailureDetails failureDetails = (FailureDetails) details;
      String actual = failureDetails.actual();
      String expected = failureDetails.expected();
      actualExpectedAttrs = actualExpectedAttrs(actual, expected);
    } else {
      // fall back
      actualExpectedAttrs = actualExpectedAttrsFromRegex(message, SPECS_COMPARISON_PATTERN);
    }
    return actualExpectedAttrs;
  }

  // hack until Specs passes a meaningful throwable.
  // https://github.com/etorreborre/specs2/issues/9
  // TODO: ^^^ it is already closed, handle it
  public static String actualExpectedAttrsFromRegex(String message, PatternWithIndices... comparisonPattern) {
    if (message == null) {
      return "";
    }
    for (PatternWithIndices patternWithIndices : comparisonPattern) {
      Matcher matcher = patternWithIndices.pattern.matcher(message);
      if (matcher.matches()) {
        String actual = matcher.group(patternWithIndices.actualIndex);
        String expected = matcher.group(patternWithIndices.expectedIndex);
        return actualExpectedAttrs(actual, expected);
      }
    }
    return "";
  }

  public static void configureReporter(String reporterQualName, boolean showProgressMessages) {
    try {
      Class<?> aClass = Class.forName(reporterQualName);
      Field field = aClass.getField("myShowProgressMessages");
      field.set(null, showProgressMessages);
    } catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<String> preprocessArgsFiles(String[] args) throws UncheckedIOException {
    return preprocessArgsFiles(Stream.of(args));
  }

  /**
   * Preprocess all argfile parameters (example: @C:/my_folder/my_file.txt) by replacing them with their contents (inlines them).
   * See {@link org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState#prepareTempArgsFile(scala.collection.Seq)}
   */
  @SuppressWarnings("JavadocReference")
  public static List<String> preprocessArgsFiles(Stream<String> args) throws UncheckedIOException {
    @SuppressWarnings("UnnecessaryLocalVariable")
    List<String> result = args
            .flatMap(arg -> arg.startsWith("@")
                    ? readLinesUnchecked(arg.substring(1))
                    : Stream.of(arg)
            )
            .collect(Collectors.toList());
    return result;
  }

  private static Stream<String> readLinesUnchecked(String fileName) throws UncheckedIOException {
    try {
      return readLines(fileName);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Stream<String> readLines(String fileName) throws IOException {
    File file = new File(fileName);
    if (!file.exists())
      throw new FileNotFoundException(String.format("argument file %s could not be found", file.getName()));
    else
      return Files.lines(file.toPath(), StandardCharsets.UTF_8).map(String::trim).filter(x -> !x.isEmpty());
  }

  public static String unescapeTestName(String testName) {
    return testName
        .replaceAll("([^\\\\])\\\\r", "$1\r")
        .replaceAll("([^\\\\])\\\\n", "$1\n")
        .replace("\\\\", "\\");
  }

  private static class PatternWithIndices {
    public final Pattern pattern;
    public final int actualIndex;
    public final int expectedIndex;
    public PatternWithIndices(Pattern pattern, int actualIndex, int expectedIndex) {
      this.pattern = pattern;
      this.actualIndex = actualIndex;
      this.expectedIndex = expectedIndex;
    }
  }
}
