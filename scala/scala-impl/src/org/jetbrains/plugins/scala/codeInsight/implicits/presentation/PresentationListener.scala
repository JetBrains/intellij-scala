package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.{Dimension, Rectangle}

trait PresentationListener {
  def contentChanged(area: Rectangle): Unit

  def sizeChanged(previous: Dimension, current: Dimension): Unit
}
