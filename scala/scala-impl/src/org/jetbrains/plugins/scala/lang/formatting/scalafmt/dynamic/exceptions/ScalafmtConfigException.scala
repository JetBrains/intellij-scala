package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

case class ScalafmtConfigException(e: String) extends Exception(e)
