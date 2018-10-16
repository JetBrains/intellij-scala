package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.Cursor
import java.awt.event._

import com.intellij.openapi.util.SystemInfo
import javax.swing.SwingUtilities

class Target(presentation0: Presentation,
             target: Presentation,
             setCursor: Cursor => Unit,
             onHover: Option[MouseEvent] => Unit,
             onClick: MouseEvent => Unit) extends DynamicForwarding(presentation0) with Hovering {

  override protected def isHovering(e: MouseEvent): Boolean =
    SystemInfo.isMac && e.isMetaDown || e.isControlDown

  override protected def processHoverEvent(event: Option[MouseEvent]): Unit = {
    val cursor = if (event.isDefined) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR
    setCursor(Cursor.getPredefinedCursor(cursor))

    delegate = if (event.isDefined) target else presentation0

    onHover(event)
  }

  override def mouseClicked(e: MouseEvent): Unit = {
    if (SwingUtilities.isLeftMouseButton(e) && isHovering(e)) {
      processHoverEvent(None)
      onClick(e)
    }
  }
}

