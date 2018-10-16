package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent

import javax.swing.SwingUtilities

class OnRightClick(presentation: Presentation, handler: MouseEvent => Unit) extends StaticForwarding(presentation) {
  override def mouseClicked(e: MouseEvent): Unit = {
    if (SwingUtilities.isRightMouseButton(e)) {
      handler(e)
    }
  }
}
