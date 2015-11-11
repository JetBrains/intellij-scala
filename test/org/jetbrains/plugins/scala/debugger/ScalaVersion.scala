package org.jetbrains.plugins.scala.debugger

import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Nikolay.Tropin
 */
trait ScalaVersion {
  protected def scalaSdkVersion: ScalaSdkVersion
  protected def scalaVersion: String = scalaSdkVersion.getMajor
}

trait ScalaVersion_2_10 extends ScalaVersion {
  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}

trait ScalaVersion_2_11 extends ScalaVersion {
  override protected val scalaSdkVersion = ScalaSdkVersion._2_11
}

trait ScalaVersion_2_12 extends ScalaVersion {
  override protected val scalaSdkVersion = ScalaSdkVersion._2_12
}