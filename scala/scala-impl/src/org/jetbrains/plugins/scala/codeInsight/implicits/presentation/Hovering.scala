package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event._

trait Hovering extends Presentation {
  protected var hovered = false

  protected def processHoverEvent(event: Option[MouseEvent]): Unit

  override def mouseMoved(e: MouseEvent): Unit = {
    if (!hovered && isHovering(e)) {
      hovered = true
      processHoverEvent(Some(e))
    }
  }

  protected def isHovering(e: MouseEvent): Boolean = true

  override def mouseExited(): Unit = {
    hovered = false
    processHoverEvent(None)
  }
}
