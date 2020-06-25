package org.jetbrains.plugins.scala.project;

/**
 * @author Pavel Fatin
 */
public enum CompileOrder implements Named {
  Mixed,
  JavaThenScala,
  ScalaThenJava;

  public String getName() {
    return CompileOrderDescriptions.get(this);
  }
}
