package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent
import java.awt.{Dimension, Graphics2D, Rectangle}

import com.intellij.openapi.editor.markup.TextAttributes

class StaticForwarding(presentation: Presentation) extends Presentation {
  override def width: Int = presentation.width

  override def height: Int = presentation.height

  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = presentation.paint(g, attributes)

  override def expand(level: Int): Unit = presentation.expand(level)

  override def mouseClicked(e: MouseEvent): Unit = presentation.mouseClicked(e)

  override def mouseMoved(e: MouseEvent): Unit = presentation.mouseMoved(e)

  override def mouseExited(): Unit = presentation.mouseExited()

  override def addPresentationListener(listener: PresentationListener): Unit = presentation.addPresentationListener(listener)

  override def removePresentationListener(listener: PresentationListener): Unit = presentation.removePresentationListener(listener)

  override def fireContentChanged(area: Rectangle): Unit = presentation.fireContentChanged(area)

  override def fireSizeChanged(previous: Dimension, current: Dimension): Unit = presentation.fireSizeChanged(previous, current)
}
