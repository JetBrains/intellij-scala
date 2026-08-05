package org.jetbrains.plugins.scala.compiler.charts.ui

sealed trait HAlign

object HAlign {

  case object Center extends HAlign
  case object Left extends HAlign
  case object Right extends HAlign
}
