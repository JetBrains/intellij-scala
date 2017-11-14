package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.jps.model.JpsElement;

import java.util.List;

/**
 * @author Maris Alexandru
 */
public interface GlobalHydraSettings extends JpsElement {
  boolean containsArtifactsFor(String scalaVersion, String hydraVersion);
  List<String> getArtifactsFor(String scalaVersion, String hydraVersion);
}
