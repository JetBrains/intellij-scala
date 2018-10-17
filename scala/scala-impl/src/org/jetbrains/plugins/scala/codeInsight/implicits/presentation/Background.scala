package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.Graphics2D

import com.intellij.openapi.editor.markup.TextAttributes

class Background(presentation: Presentation) extends StaticForwarding(presentation)  {
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    Option(attributes.getBackgroundColor).foreach { color =>
      g.setColor(color)
      g.fillRect(0, 0, width, height)
    }

    super.paint(g, attributes)
  }
}
