package org.jetbrains.jps.incremental.scala.local;

import org.jetbrains.plugins.scala.compiler.data.CompilerData;
import org.jetbrains.plugins.scala.compiler.data.SbtData;

public interface CompilerFactoryService {
  boolean isEnabled(CompilerData compilerData);
  CompilerFactory get(SbtData sbtData);
}
