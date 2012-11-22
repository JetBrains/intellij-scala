package org.jetbrains.jps.incremental.scala.model;

/**
 * @author Pavel Fatin
 */
public interface CompilerLibraryHolder {
  LibraryLevel getCompilerLibraryLevel();

  String getCompilerLibraryName();
}
