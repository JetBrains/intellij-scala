package org.jetbrains.plugins.scala.project;

/**
 * @author Pavel Fatin
 */
public enum CompileOrder implements Named {
  Mixed("Mixed"),
  JavaThenScala("Java then Scala"),
  ScalaThenJava("Scala then Java");

  private String myName;

  CompileOrder(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
