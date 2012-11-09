package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author Pavel Fatin
 */
public interface CompilerSettings extends JpsElement {
  boolean isScalaFirst();
}
