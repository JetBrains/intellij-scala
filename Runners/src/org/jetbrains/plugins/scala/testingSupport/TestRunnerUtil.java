package org.jetbrains.plugins.scala.testingSupport;

import org.specs2.execute.Details;
import org.specs2.execute.FailureDetails;
import org.specs2.execute.FailureException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRunnerUtil {
  public static final Pattern COMPARISON_PATTERN = Pattern.compile("'(.+)' is not equal to '(.*)'", Pattern.MULTILINE | Pattern.DOTALL);
  public static final Pattern LOCATION_PATTERN = Pattern.compile("(\\S+)( \\((.+)\\))?");

  // from ServiceMessage
  private static final String FORMAT_WITHOUT_TZ = "yyyy-MM-dd'T'HH:mm:ss.SSS";
  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(FORMAT_WITHOUT_TZ);

  public static String escapeString(String s) {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]");
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

  public static String actualExpectedAttrsSpecs2(String message, Details details) {
    String actualExpectedAttrs = "";
    if (details != null && details instanceof FailureDetails) {
      FailureDetails failureDetails = (FailureDetails) details;
      String actual = failureDetails.actual();
      String expected = failureDetails.expected();
      actualExpectedAttrs = actualExpectedAttrs(actual, expected);
    } else {
      // fall back
      actualExpectedAttrs = actualExpectedAttrsFromRegex(message);
    }
    return actualExpectedAttrs;
  }

  // hack until Specs passes a meaningful throwble.
  // https://github.com/etorreborre/specs2/issues/9
  public static String actualExpectedAttrsFromRegex(String message) {
    if (message == null) {
      return "";
    }
    Matcher matcher = COMPARISON_PATTERN.matcher(message);
    if (matcher.matches()) {
      String actual = matcher.group(1);
      String expected = matcher.group(2);
      return actualExpectedAttrs(actual, expected);
    }
    return "";
  }

  public static Location parseLocation(String location) {
    Location location1 = new Location();
    Matcher matcher = LOCATION_PATTERN.matcher(location);
    if (matcher.matches()) {
      location1.className = matcher.group(1);
      if (matcher.groupCount() == 3) {
        location1.fileNameAndLine = matcher.group(3);
      }
    }
    return location1;
  }

  public static class Location {
    String className;
    String fileNameAndLine;

    public String toHint() {
      String s = "locationHint='scala://" + escapeString(className);
      if (fileNameAndLine != null && !fileNameAndLine.isEmpty()) {
        s += "?filelocation=" + escapeString(fileNameAndLine);
      }
      s += "'";
      return s;
    }
  }
}
