package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author Pavel Fatin
 */
public interface FacetSettings extends JpsElement {
  LibraryLevel getCompilerLibraryLevel();

  String getCompilerLibraryName();

  Order getCompileOrder();

  String[] getCompilerOptions();
}
