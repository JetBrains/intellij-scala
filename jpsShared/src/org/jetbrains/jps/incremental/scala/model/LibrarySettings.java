package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

import java.io.File;

/**
 * @author Pavel Fatin
 */
public interface LibrarySettings extends JpsElement {
  File[] getCompilerClasspath();
}
