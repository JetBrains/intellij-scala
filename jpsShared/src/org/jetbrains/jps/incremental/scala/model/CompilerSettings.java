package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.incremental.scala.data.SbtIncrementalOptions;

/**
 * @author Pavel Fatin
 */
public interface CompilerSettings {
  CompileOrder getCompileOrder();

  SbtIncrementalOptions getSbtIncrementalOptions();

  String[] getCompilerOptions();
}
