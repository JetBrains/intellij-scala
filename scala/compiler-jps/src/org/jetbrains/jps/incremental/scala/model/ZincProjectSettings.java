package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

public interface ZincProjectSettings extends JpsElement {
    boolean isCompileToJar();
    boolean isIgnoringScalacOptions();
    String[] getIgnoredScalacOptions();
}
