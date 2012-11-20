package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author Pavel Fatin
 */
public interface FacetSettings extends JpsElement, CompilerLibraryHolder {
  LibraryLevel getCompilerLibraryLevel();

  String getCompilerLibraryName();

  boolean isFscEnabled();
}
