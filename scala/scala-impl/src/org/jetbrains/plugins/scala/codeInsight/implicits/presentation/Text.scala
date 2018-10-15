package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.{Color, Font, Graphics2D}

import com.intellij.openapi.editor.markup.TextAttributes

class Text(override val width: Int, override val height: Int,
           text: String, val font: Font, color: Option[Color], ascent: Int) extends Presentation {

  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    Option(attributes.getForegroundColor).orElse(color).foreach { foreground =>
      g.setFont(font)
      g.setColor(foreground)
      g.drawString(text, 0, ascent)
    }

    // todo font type?
  }
}
