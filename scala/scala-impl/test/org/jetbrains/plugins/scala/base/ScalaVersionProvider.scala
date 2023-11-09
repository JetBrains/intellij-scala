package org.jetbrains.plugins.scala.base

import org.jetbrains.plugins.scala.ScalaVersion

trait ScalaVersionProvider {

  //TODO: rename to scalaVersion or something else more meaningful
  def version: ScalaVersion
}
