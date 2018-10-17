package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.{Font, Graphics2D}

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes

class Text(override val width: Int, override val height: Int,
           text: String, ascent: Int, fontProvider: EditorFontType => Font) extends Presentation {

  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    Option(attributes.getForegroundColor).foreach { foreground =>
      val fontType = attributes.getFontType match {
        case Font.BOLD => EditorFontType.BOLD
        case Font.ITALIC => EditorFontType.ITALIC
        case _ => EditorFontType.PLAIN
      }
      g.setFont(fontProvider(fontType))
      g.setColor(foreground)
      g.drawString(text, 0, ascent)
    }
  }
}
