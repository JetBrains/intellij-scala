package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.{Color, Graphics2D}

import com.intellij.openapi.editor.markup.TextAttributes

class Background(presentation: Presentation, color: Option[Color]) extends StaticForwarding(presentation)  {
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    Option(attributes.getBackgroundColor).orElse(color).foreach { color =>
      g.setColor(color)
      g.fillRect(0, 0, width, height)
    }

    presentation.paint(g, attributes)
  }
}
