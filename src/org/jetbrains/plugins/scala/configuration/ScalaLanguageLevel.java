package org.jetbrains.plugins.scala.configuration;

/**
 * @author Pavel Fatin
 */
public enum ScalaLanguageLevel implements Named {
  SCALA_2_9("Scala 2.9"),
  SCALA_2_10("Scala 2.10"),
  SCALA_2_10_VIRTUALIZED("Scala 2.10 virtualized"),
  SCALA_2_11("Scala 2.11"),
  SCALA_2_11_VIRTUALIZED("Scala 2.11 virtualized");

  private String myName;

  ScalaLanguageLevel(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
