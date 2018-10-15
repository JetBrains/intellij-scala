package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent

import javax.swing.SwingUtilities

class Expansion(collapsed: Presentation, expanded: => Presentation) extends DynamicPresentation(collapsed) {
  private lazy val expandedPresentation = expanded

  override def expand(level: Int): Unit = {
    delegate = if (level > 0) expandedPresentation else collapsed
    delegate.expand(0.max(level - 1))
  }

  override def mouseClicked(e: MouseEvent): Unit = {
    if (delegate == collapsed && SwingUtilities.isLeftMouseButton(e)) {
      expand(1)
    } else {
      delegate.mouseClicked(e)
    }
  }
}
