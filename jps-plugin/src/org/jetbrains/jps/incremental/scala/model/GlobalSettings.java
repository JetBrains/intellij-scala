package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.plugin.scala.compiler.CompileOrder;
import org.jetbrains.plugin.scala.compiler.IncrementalType;

/**
 * @author Pavel Fatin
 */
public interface GlobalSettings extends JpsElement {
  boolean isCompileServerEnabled();

  int getCompileServerPort();

  String getCompileServerSdk();

  public IncrementalType getIncrementalType();

  public CompileOrder getCompileOrder();
}
