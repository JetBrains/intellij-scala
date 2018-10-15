package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D

import com.intellij.openapi.editor.markup.TextAttributes

class Rounding(presentation: Presentation, arcWidth: Int, arcHeight: Int) extends DynamicPresentation(presentation) {
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    g.setClip(new RoundRectangle2D.Double(0, 0, width, height, arcWidth, arcHeight))
    presentation.paint(g, attributes)
    g.setClip(null)
  }
}
