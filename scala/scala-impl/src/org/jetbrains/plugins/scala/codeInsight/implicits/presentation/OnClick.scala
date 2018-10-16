package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent

import javax.swing.SwingUtilities

class OnClick(presentation: Presentation, button: Button, handler: MouseEvent => Unit) extends StaticForwarding(presentation) {
  override def mouseClicked(e: MouseEvent): Unit = {
    val expectedButton = button match {
      case Button.Left => SwingUtilities.isLeftMouseButton(e)
      case Button.Middle => SwingUtilities.isMiddleMouseButton(e)
      case Button.Right => SwingUtilities.isRightMouseButton(e)
    }
    if (expectedButton) {
      handler(e)
    }
  }
}
