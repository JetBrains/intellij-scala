package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author Pavel Fatin
 */
public interface ProjectSettings extends JpsElement {
  IncrementalityType getIncrementalityType();

  CompileOrder getCompileOrder();

  String[] getCompilerOptions();
}
