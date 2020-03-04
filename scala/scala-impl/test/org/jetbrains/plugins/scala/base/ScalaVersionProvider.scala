package org.jetbrains.plugins.scala.base

import org.jetbrains.plugins.scala.ScalaVersion

trait ScalaVersionProvider {

  def version: ScalaVersion
}
