package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.plugins.scala.compiler.data.CompileOrder;
import org.jetbrains.plugins.scala.compiler.data.SbtIncrementalOptions;

import java.util.List;

public interface CompilerSettings {
  CompileOrder getCompileOrder();

  SbtIncrementalOptions getSbtIncrementalOptions();

  List<String> getCompilerOptionsAsStrings(boolean forScala3Compiler);
}
