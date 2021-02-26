package org.scalatest.finders.utils;

import java.util.Collections;
import java.util.HashSet;

public final class CollectionUtils {

  private CollectionUtils() {
  }

  public static <T> HashSet<T> newHasSet(T... elements) {
    HashSet<T> set = new HashSet<T>();
    Collections.addAll(set, elements);
    return set;
  }
}
