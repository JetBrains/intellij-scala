package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent
import java.awt.{Dimension, Graphics2D, Rectangle}

import com.intellij.openapi.editor.markup.TextAttributes

class DynamicForwarding(presentation: Presentation) extends Presentation {
  private var _delegate = presentation

  _delegate.addPresentationListener(Listener)

  protected def delegate: Presentation = _delegate

  protected def delegate_=(presentation: Presentation): Unit = {
    if (_delegate != presentation) {
      val previousWidth = _delegate.width
      presentation.removePresentationListener(Listener)
      _delegate = presentation
      presentation.addPresentationListener(Listener)

      if (presentation.width == previousWidth) {
        fireContentChanged(new Rectangle(0, 0, width, 0)) // todo
      } else {
        fireSizeChanged(new Dimension(previousWidth, 0), new Dimension(presentation.width, 0)) // todo
      }
    }
  }

  override def width: Int = delegate.width

  override def height: Int = delegate.height

  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = delegate.paint(g, attributes)

  override def expand(level: Int): Unit = delegate.expand(level)

  override def mouseClicked(e: MouseEvent): Unit = delegate.mouseClicked(e)

  override def mouseMoved(e: MouseEvent): Unit = delegate.mouseMoved(e)

  override def mouseExited(): Unit = delegate.mouseExited()

  private object Listener extends PresentationListener {
    override def contentChanged(area: Rectangle): Unit = fireContentChanged(area)

    override def sizeChanged(previous: Dimension, current: Dimension): Unit = fireSizeChanged(previous, current)
  }
}
