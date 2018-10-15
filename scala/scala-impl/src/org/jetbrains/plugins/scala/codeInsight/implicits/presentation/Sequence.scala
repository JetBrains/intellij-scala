package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent
import java.awt.{Component, Dimension, Graphics2D, Rectangle}

import com.intellij.openapi.editor.markup.TextAttributes

class Sequence(override val height: Int, presentations: Presentation*) extends Presentation {
  private var _presentationUnderCursor: Option[Presentation] = None

  presentations.foreach(_.addPresentationListener(Listener))

  override def width: Int = presentations.map(_.width).sum

  override def expand(level: Int): Unit = presentations.foreach(_.expand(level))

  // falback, getOriginalTransform
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    presentations.foreach { presentation =>
      presentation.paint(g, attributes)
      g.translate(presentation.width, 0)
    }
    g.translate(-width, 0)
  }

  override def mouseClicked(e: MouseEvent): Unit = {
    val (x, presentation) = targetOf(e)
    presentation.mouseClicked(shifted(e, -x, 0))
  }

  override def mouseMoved(e: MouseEvent): Unit = {
    val (x, presentation) = targetOf(e)
    if (!_presentationUnderCursor.contains(presentation)) {
      _presentationUnderCursor.foreach(_.mouseExited())
      _presentationUnderCursor = Some(presentation)
    }
    presentation.mouseMoved(shifted(e, -x, 0))
  }

  override def mouseExited(): Unit = {
    _presentationUnderCursor.foreach(_.mouseExited())
    _presentationUnderCursor = None
  }

  private def shifted(e: MouseEvent, dx: Int, dy: Int): MouseEvent =
    new MouseEvent(e.getSource.asInstanceOf[Component], e.getID, e.getWhen, e.getModifiers,
      e.getX + dx, e.getY + dy, e.getXOnScreen, e.getYOnScreen, e.getClickCount, e.isPopupTrigger, e.getButton)

  // todo
  private def targetOf(e: MouseEvent): (Int, Presentation) = {
    presentations.foldLeft(0) { (x, presentation) =>
      if (x <= e.getX && e.getX < x + presentation.width) {
        return (x, presentation)
      }
      x + presentation.width
    }
    throw new RuntimeException()
  }

  private object Listener extends PresentationListener {
    override def contentChanged(area: Rectangle): Unit = fireContentChanged(new Rectangle(0, 0, 0, 0)) // todo

    override def sizeChanged(previous: Dimension, current: Dimension): Unit = {
      val dx = current.width - previous.width
      fireSizeChanged(new Dimension(width - dx, 0), new Dimension(width, 0)) // todo
    }
  }
}
