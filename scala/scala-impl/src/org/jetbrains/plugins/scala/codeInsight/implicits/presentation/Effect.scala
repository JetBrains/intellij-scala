package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.{Font, Graphics2D}

import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.ui.paint.EffectPainter

class Effect(presentation: Presentation, font: Font, lineHeight: Int, ascent: Int, descent: Int) extends StaticForwarding(presentation) {
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    presentation.paint(g, attributes)

    Option(attributes.getEffectColor).foreach { effectColor =>
      g.setColor(effectColor)

      attributes.getEffectType match {
        case EffectType.LINE_UNDERSCORE => EffectPainter.LINE_UNDERSCORE.paint(g, 0, ascent, width, descent, font)
        case EffectType.BOLD_LINE_UNDERSCORE => EffectPainter.BOLD_LINE_UNDERSCORE.paint(g, 0, ascent, width, descent, font)
        case EffectType.STRIKEOUT => EffectPainter.STRIKE_THROUGH.paint(g, 0, ascent, width, height, font)
        case EffectType.WAVE_UNDERSCORE => EffectPainter.WAVE_UNDERSCORE.paint(g, 0, ascent, width, descent, font)
        case EffectType.BOLD_DOTTED_LINE => EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g, 0, ascent, width, descent, font)
        case _ =>
      }
    }
  }
}
