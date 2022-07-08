package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.plugins.scala.compiler.data.CompileOrder;
import org.jetbrains.plugins.scala.compiler.data.SbtIncrementalOptions;

public interface CompilerSettings {
  CompileOrder getCompileOrder();

  SbtIncrementalOptions getSbtIncrementalOptions();

  String[] getCompilerOptionsAsStrings(boolean forScala3Compiler);
}
