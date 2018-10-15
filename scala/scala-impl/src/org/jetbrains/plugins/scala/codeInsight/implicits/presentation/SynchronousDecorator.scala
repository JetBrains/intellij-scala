package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent

import com.intellij.openapi.util.SystemInfo

private class SynchronousDecorator private (decorator: Presentation => Presentation, presentation: Presentation)
  extends DynamicPresentation(presentation) with Hovering {

  private var others = Seq.empty[SynchronousDecorator]

  override protected def isHovering(e: MouseEvent): Boolean =
    SystemInfo.isMac && e.isMetaDown || e.isControlDown

  override protected def processHoverEvent(point: Option[MouseEvent]): Unit = {
    (this +: others).foreach(_.update(point.isDefined))
  }

  private def update(hovered: Boolean): Unit = {
    delegate = if (hovered) decorator(presentation) else presentation
  }
}

object SynchronousDecorator {
  def apply(decorator: Presentation => Presentation, presentations: Presentation*): Seq[Presentation] = {
    val result = presentations.map(new SynchronousDecorator(decorator, _))
    result.foreach(it => it.others = result.filterNot(_ == it))
    result
  }
}
