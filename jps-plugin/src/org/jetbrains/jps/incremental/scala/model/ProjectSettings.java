package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author Pavel Fatin
 */
public interface ProjectSettings extends JpsElement, CompilerLibraryHolder {
  Order getCompilationOrder();

  LibraryLevel getCompilerLibraryLevel();

  String getCompilerLibraryName();

  boolean isCompilationServerEnabled();

  int getCompilationServerPort();
}
