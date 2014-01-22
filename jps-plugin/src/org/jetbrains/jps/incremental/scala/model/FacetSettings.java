package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.plugin.scala.compiler.CompileOrder;

/**
 * @author Pavel Fatin
 */
public interface FacetSettings extends JpsElement {
  LibraryLevel getCompilerLibraryLevel();

  String getCompilerLibraryName();

  CompileOrder getCompileOrder();

  String[] getCompilerOptions();
}
