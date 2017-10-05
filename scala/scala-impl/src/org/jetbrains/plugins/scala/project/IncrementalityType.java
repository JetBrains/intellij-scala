package org.jetbrains.plugins.scala.project;

/**
 * @author Pavel Fatin
 */
public enum IncrementalityType implements Named  {
  IDEA("IDEA"),
  SBT("SBT");

  private String myName;

  IncrementalityType(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
