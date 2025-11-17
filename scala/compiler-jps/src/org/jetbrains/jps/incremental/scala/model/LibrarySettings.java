package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

import java.nio.file.Path;

public interface LibrarySettings extends JpsElement {
  Path[] getCompilerClasspath();
  @Nullable Path getCompilerBridgeJar();
  Path[] getReplClasspath();
}
