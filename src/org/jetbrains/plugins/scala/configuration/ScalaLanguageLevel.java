package org.jetbrains.plugins.scala.configuration;

import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel Fatin
 */
public enum ScalaLanguageLevel implements Named {
  SCALA_2_9("2.9", false),
  SCALA_2_10("2.10", false),
  SCALA_2_10_VIRTUALIZED("2.10", true),
  SCALA_2_11("2.11", false),
  SCALA_2_11_VIRTUALIZED("2.11", true);

  private String myVersion;
  private boolean myVirtualized;

  ScalaLanguageLevel(String version, boolean virtualized) {
    myVersion = version;
    myVirtualized = virtualized;
  }

  public String getName() {
    String prefix = "Scala " + myVersion;
    return myVirtualized ? prefix + " virtualized" : prefix;
  }

  public boolean isVirtualized() {
    return myVirtualized;
  }

  public boolean isSinceScala2_10() {
    return this.compareTo(SCALA_2_10) >= 0;
  }

  public boolean isSinceScala2_11() {
    return this.compareTo(SCALA_2_11) >= 0;
  }

  public static ScalaLanguageLevel getDefault() {
    return SCALA_2_10;
  }

  @Nullable
  public static ScalaLanguageLevel from(String version, boolean withDefault) {
    for (ScalaLanguageLevel level : ScalaLanguageLevel.values()) {
      if (version.startsWith(level.myVersion)) return level;
    }
    return withDefault ? getDefault() : null;
  }
}
