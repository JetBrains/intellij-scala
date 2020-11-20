package org.jetbrains.plugins.scala.compilationCharts.ui

sealed trait VAlign

object VAlign {

  final case object Center extends VAlign
  final case object Bottom extends VAlign
  final case object Top extends VAlign
}
