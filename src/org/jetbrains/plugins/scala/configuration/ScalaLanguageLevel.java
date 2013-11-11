package org.jetbrains.plugins.scala.configuration;

/**
 * @author Pavel Fatin
 */
public enum ScalaLanguageLevel implements Named {
  SCALA_2_9("Scala 2.9", false),
  SCALA_2_10("Scala 2.10", false),
  SCALA_2_10_VIRTUALIZED("Scala 2.10 virtualized", true),
  SCALA_2_11("Scala 2.11", false),
  SCALA_2_11_VIRTUALIZED("Scala 2.11 virtualized", true);

  private String myName;
  private boolean myVirtualized;

  ScalaLanguageLevel(String name, boolean virtualized) {
    myName = name;
    myVirtualized = virtualized;
  }

  public String getName() {
    return myName;
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
}
