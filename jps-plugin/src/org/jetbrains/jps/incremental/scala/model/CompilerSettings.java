package org.jetbrains.jps.incremental.scala.model;

/**
 * @author Pavel Fatin
 */
public interface CompilerSettings {
  IncrementalityType getIncrementalityType();

  CompileOrder getCompileOrder();

  String[] getCompilerOptions();
}
