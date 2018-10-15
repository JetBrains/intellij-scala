package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent

trait InputEvents {
  def mouseClicked(e: MouseEvent): Unit = {}

  def mouseMoved(e: MouseEvent): Unit = {}

  def mouseExited(): Unit = {}
}
