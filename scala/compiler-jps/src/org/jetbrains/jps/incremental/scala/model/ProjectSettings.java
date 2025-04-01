package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType;

public interface ProjectSettings extends JpsElement {
  IncrementalityType getIncrementalityType();

  CompilerSettings getCompilerSettings(ModuleChunk chunk);

  Boolean getExternalRootPathToSeparateMainTestModules(@Nullable String externalRootPath);
}
