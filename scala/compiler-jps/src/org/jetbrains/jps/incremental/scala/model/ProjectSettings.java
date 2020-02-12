package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.plugins.scala.compiler.IncrementalityType;

/**
 * @author Pavel Fatin
 */
public interface ProjectSettings extends JpsElement {
  IncrementalityType getIncrementalityType();

  CompilerSettings getCompilerSettings(ModuleChunk chunk);
}
