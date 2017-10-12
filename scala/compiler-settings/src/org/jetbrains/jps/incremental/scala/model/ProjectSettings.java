package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author Pavel Fatin
 */
public interface ProjectSettings extends JpsElement {
  IncrementalityType getIncrementalityType();

  CompilerSettings getCompilerSettings(ModuleChunk chunk);
}
