package org.jetbrains.plugins.scala.testingSupport

trait TestOutputMarkers {

  val TestOutputPrefix = ">>TEST:"
  val TestOutputSuffix = "<<"
}

object TestOutputMarkers extends TestOutputMarkers