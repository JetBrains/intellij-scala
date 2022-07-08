package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

public interface GlobalSettings extends JpsElement {
  boolean isCompileServerEnabled();

  int getCompileServerPort();

  String getCompileServerSdk();
}
