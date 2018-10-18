package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent

class OnHover(presentation: Presentation, filter: MouseEvent => Boolean, handler: Option[MouseEvent] => Unit) extends StaticForwarding(presentation) {
  private var hovered = false

  override def mouseMoved(e: MouseEvent): Unit = {
    if (!hovered && filter(e)) {
      hovered = true
      handler(Some(e))
    }

    super.mouseMoved(e)
  }

  override def mouseExited(): Unit = {
    hovered = false
    handler(None)

    super.mouseExited()
  }
}
