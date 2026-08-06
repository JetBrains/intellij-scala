package org.scalatest.finders.utils;

import org.scalatest.finders.MethodInvocation;

public final class StringUtils {

  private StringUtils() {
  }

  public static boolean is(String value, String... equalTo) {
    for (String s : equalTo) {
      if (value.equals(s))
        return true;
    }
    return false;
  }

  public static boolean isMethod(MethodInvocation method, String... equalTo) {
    for (String s : equalTo) {
      if (method.name().equals(s))
        return true;
    }
    return false;
  }
}
