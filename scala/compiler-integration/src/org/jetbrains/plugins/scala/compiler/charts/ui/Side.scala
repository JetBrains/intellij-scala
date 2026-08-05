package org.jetbrains.plugins.scala.compiler.charts.ui

sealed trait Side

object Side {

  case object North extends Side
  case object South extends Side
  case object West extends Side
  case object East extends Side
}
