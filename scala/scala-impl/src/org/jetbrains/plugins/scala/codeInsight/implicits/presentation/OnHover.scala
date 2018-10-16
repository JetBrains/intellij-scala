package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent

class OnHover(presentation: Presentation, filter: MouseEvent => Boolean, handler: Option[MouseEvent] => Unit) extends StaticForwarding(presentation) with Hovering {
  override protected def processHoverEvent(point: Option[MouseEvent]): Unit = handler(point)

  override protected def isHovering(e: MouseEvent): Boolean = filter(e)
}
