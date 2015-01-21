package org.jetbrains.jps.incremental.scala.model;

/**
 * @author Pavel Fatin
 */
public interface CompilerSettings {
  CompileOrder getCompileOrder();

  String[] getCompilerOptions();
}
