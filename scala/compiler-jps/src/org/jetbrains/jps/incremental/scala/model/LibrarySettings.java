package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

import java.io.File;

public interface LibrarySettings extends JpsElement {
  File[] getCompilerClasspath();
  @Nullable File getCompilerBridgeJar();
}
