package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

import java.util.List;
import java.util.Map;

/**
 * @author Maris Alexandru
 */
public interface HydraSettings extends JpsElement {
  boolean isHydraEnabled();
  String getHydraVersion();
  Map<String, List<String>> getArtifactPaths();
}
