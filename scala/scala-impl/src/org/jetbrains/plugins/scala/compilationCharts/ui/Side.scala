package org.jetbrains.plugins.scala.compilationCharts.ui

sealed trait Side

object Side {

  final case object North extends Side
  final case object South extends Side
  final case object West extends Side
  final case object East extends Side
}
