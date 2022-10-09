package org.jetbrains.plugins.scala.testingSupport.specs2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;

public class Spec2Utils {

  private static final Pattern LOCATION_PATTERN = Pattern.compile("(\\S+)( \\((.+)\\))?");

  public static Location parseLocation(String locationString) {
    final Location location;
    Matcher matcher = LOCATION_PATTERN.matcher(locationString);
    if (matcher.matches()) {
      String className = matcher.group(1);
      String fileNameAndLIne = matcher.groupCount() == 3 ? matcher.group(3) : null;
      location = new Location(className, fileNameAndLIne);
    }
    else {
      location = new Location(null, null);
    }
    return location;
  }

  public static class Location {
    final String className;
    final String fileNameAndLine;

    public Location(String className, String fileNameAndLine) {
      this.className = className;
      this.fileNameAndLine = fileNameAndLine;
    }

    public String toHint() {
      if (className == null) return "";
      String s = " locationHint='scala://" + escapeString(className);
      if (fileNameAndLine != null && !fileNameAndLine.isEmpty()) {
        s += "?filelocation=" + escapeString(fileNameAndLine);
      }
      s += "'";
      return s;
    }
  }
}
