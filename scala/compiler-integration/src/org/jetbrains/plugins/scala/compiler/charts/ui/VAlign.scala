package org.jetbrains.plugins.scala.compiler.charts.ui

sealed trait VAlign

object VAlign {

  case object Center extends VAlign
  case object Bottom extends VAlign
  case object Top extends VAlign
}
