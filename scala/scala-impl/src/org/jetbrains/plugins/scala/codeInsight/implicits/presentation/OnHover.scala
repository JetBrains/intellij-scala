package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent

class OnHover(presentation: Presentation, handler: Option[MouseEvent] => Unit) extends StaticForwarding(presentation) with Hovering {
  override protected def processHoverEvent(point: Option[MouseEvent]): Unit = handler(point)
}
