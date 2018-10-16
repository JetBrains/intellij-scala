package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

sealed trait Button

object Button {
  case object Left extends Button
  case object Middle extends Button
  case object Right extends Button
}
