package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.specs2.reporter.Notifier;
import org.specs2.reporter.NotifierReporter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Notifier implements Notifier {
  private HashMap<String, Long> map = new HashMap<String, Long>();

  private static String escapeString(String s) {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]");
  }

  public void specStart(String title, String location) {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(title) + "' "+ parseLocation(location).toHint() + "]");
  }

  public void specEnd(String title, String location) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(title) + "']");
  }

  public void contextStart(String text, String location) {
  }

  public void contextEnd(String text, String location) {
  }

  public void text(String text, String location) {
  }

  public void exampleStarted(String name, String location) {
    System.out.println("\n##teamcity[testStarted name='" + escapeString(name) + "' " + parseLocation(location).toHint() +
            " captureStandardOutput='true']");
  }

  static class Location {
    String className;
    String fileNameAndLine;

    String toHint() {
      String s = "locationHint='scala://" + escapeString(className);
      if (fileNameAndLine != null && !fileNameAndLine.isEmpty()) {
        s += "?filelocation=" + escapeString(fileNameAndLine);
      }
      s += "'";
      return s;
    }
  }

  private static final Pattern LOCATION_PATTERN = Pattern.compile("(\\S+)( \\((.+)\\))?");

  private Location parseLocation(String location) {
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

  public void exampleSuccess(String text, long duration) {
    System.out.println("\n##teamcity[testFinished name='" + escapeString(text) + "' duration='"+ duration +"']");
  }

  public void exampleFailure(String name, String message, String location, Throwable f, long duration) {
    boolean error = true;
    String detail;
    if (f instanceof AssertionError) error = false;
    StringWriter writer = new StringWriter();
    f.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();
    String res = "\n##teamcity[testFailed name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += "timestamp='" + escapeString(message) + "']";
    System.out.println(res);
    exampleSuccess(message, 0);
  }

  public void exampleError(String name, String message, String location, Throwable f, long duration) {
    exampleFailure(name, message, location, f,  duration);
  }

  public void exampleSkipped(String name, String message, long duration) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(message) + "' message='" + escapeString(message) + "']");
  }

  public void examplePending(String name, String message, long duration) {
  }
}
