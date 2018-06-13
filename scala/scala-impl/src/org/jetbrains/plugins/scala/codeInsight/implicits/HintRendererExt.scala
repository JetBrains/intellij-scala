package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt._

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.ui.paint.EffectPainter
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.plugins.scala.codeInsight.implicits.HintRendererExt._

// TODO Support custom margin & pading in the IDEA's HintRenderer
class HintRendererExt(text: String) extends HintRenderer(text) {
  protected def getMargin(editor: Editor): Insets = DefaultMargin

  protected def getPadding(editor: Editor): Insets = DefaultPadding

  override protected def calcWidthInPixels(editor: Editor): Int = {
    val m = getMargin(editor)
    val p = getPadding(editor)
    val fontMetrics = getFontMetrics(editor).getMetrics
    if (text == null) 0 else fontMetrics.stringWidth(text) + m.left + p.left + p.right + m.right
  }

  override def paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    if (!editor.isInstanceOf[EditorImpl]) return
    val editorImpl = editor.asInstanceOf[EditorImpl]

    val m = getMargin(editor)
    val p = getPadding(editor)

    val ascent = editorImpl.getAscent
    val descent = editorImpl.getDescent
    val g2d = g.asInstanceOf[Graphics2D]
    val attributes = getTextAttributes(editor)
    if (text != null && attributes != null) {
      val fontMetrics = getFontMetrics(editor)
      val gap = if (r.height < fontMetrics.getLineHeight + 2) 1 else 2
      val backgroundColor = attributes.getBackgroundColor
      if (backgroundColor != null) {
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.setColor(backgroundColor)
        g.fillRoundRect(r.x + m.left, r.y + gap, r.width - m.left - m.right, r.height - gap * 2, 8, 8)
        config.restore()
      }
      val foregroundColor = attributes.getForegroundColor
      if (foregroundColor != null) {
        val savedHint = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
        val savedClip = g.getClip

        g.setColor(foregroundColor)
        g.setFont(getFont(editor))
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
        g.clipRect(r.x + m.left + 1, r.y + 2, r.width - m.left - m.right - 2, r.height - 4)
        val metrics = fontMetrics.getMetrics
        g.drawString(text, r.x + m.left + p.left, r.y + Math.max(ascent, (r.height + metrics.getAscent - metrics.getDescent) / 2) - 1)

        g.setClip(savedClip)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
      }
    }
    val effectColor = textAttributes.getEffectColor
    val effectType = textAttributes.getEffectType
    if (effectColor != null) {
      g.setColor(effectColor)
      val xStart = r.x
      val xEnd = r.x + r.width
      val y = r.y + ascent
      val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)

      effectType match {
        case EffectType.LINE_UNDERSCORE => EffectPainter.LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
        case EffectType.BOLD_LINE_UNDERSCORE => EffectPainter.BOLD_LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
        case EffectType.STRIKEOUT => EffectPainter.STRIKE_THROUGH.paint(g2d, xStart, y, xEnd - xStart, editorImpl.getCharHeight, font)
        case EffectType.WAVE_UNDERSCORE => EffectPainter.WAVE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
        case EffectType.BOLD_DOTTED_LINE => EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
      }
    }
  }

  private def getFont(editor: Editor): Font = {
    getFontMetrics(editor).getFont
  }
}

private object HintRendererExt {
  final val BACKGROUND_ALPHA = 0.55f

  final val DefaultMargin = new Insets(0, 2, 0, 2)

  final val DefaultPadding = new Insets(0, 5, 0, 5)
}

