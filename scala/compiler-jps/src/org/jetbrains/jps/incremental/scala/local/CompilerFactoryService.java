package org.jetbrains.jps.incremental.scala.local;

import org.jetbrains.jps.incremental.scala.data.CompilerData;
import org.jetbrains.jps.incremental.scala.data.SbtData;

public interface CompilerFactoryService {
  boolean isEnabled(CompilerData compilerData);
  CompilerFactory get(SbtData sbtData);
}
