package org.jetbrains.jps.incremental.scala.model;

/**
 * @author Pavel Fatin
 */
public enum CompileOrder {
  Mixed,
  JavaThenScala,
  ScalaThenJava
}
