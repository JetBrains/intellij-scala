package org.jetbrains.plugins.scala.debugger

/**
 * @author Nikolay.Tropin
 */
trait ScalaVersion {
  protected val scalaVersion: String
}

trait ScalaVersion_2_10 extends ScalaVersion {
  override protected val scalaVersion: String = "2.10"
}

trait ScalaVersion_2_11 extends ScalaVersion {
  override protected val scalaVersion: String = "2.11"
}

trait ScalaVersion_2_12_M2 extends ScalaVersion {
  override protected val scalaVersion: String = "2.12.0.M2"
}