package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt._

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.openapi.util.Key
import com.intellij.ui.paint.EffectPainter
import com.intellij.util.ui.GraphicsUtil
import javax.swing.UIManager
import org.jetbrains.plugins.scala.codeInsight.implicits.HintRendererExt._

private class HintRendererExt(private var parts: Seq[Text]) extends HintRenderer(parts.map(_.string).mkString) {
  protected def getMargin(editor: Editor): Insets = DefaultMargin

  protected def getPadding(editor: Editor): Insets = DefaultPadding

  override protected def calcWidthInPixels(editor: Editor): Int = {
    val m = getMargin(editor)
    val p = getPadding(editor)
    val metrics = getFontMetrics0(editor).getMetrics
    metrics.stringWidth(getText) + m.left + p.left + p.right + m.right
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
    if (attributes != null) {
      val fontMetrics = getFontMetrics0(editor)
      val gap = if (r.height < fontMetrics.getLineHeight + 2) 1 else 2
      val backgroundColor = attributes.getBackgroundColor
      if (backgroundColor != null) {
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BackgroundAlpha)
        g.setColor(backgroundColor)
        g.fillRoundRect(r.x + m.left, r.y + gap, r.width - m.left - m.right, r.height - gap * 2, 8, 8)
        config.restore()
      }
      val foregroundColor = attributes.getForegroundColor
      if (foregroundColor != null) {
        val metrics = fontMetrics.getMetrics
        var xStart = r.x + m.left + p.left
        val yStart = r.y + Math.max(ascent, (r.height + metrics.getAscent - metrics.getDescent) / 2) - 1

        g.setFont(getFont(editor))

        parts.foreach { text =>
          val width = metrics.stringWidth(text.string)

          val effectiveTextAttributes = text.effective(editor, attributes)

          val backgroundColor = effectiveTextAttributes.getBackgroundColor
          if (backgroundColor != null) {
            val config = GraphicsUtil.setupAAPainting(g)
            GraphicsUtil.paintWithAlpha(g, BackgroundAlpha)
            g.setColor(backgroundColor)
            g.fillRect(xStart, r.y + gap, width, r.height - gap * 2)
            config.restore()
          }

          val foregroundColor = effectiveTextAttributes.getForegroundColor
          g.setColor(foregroundColor)

          val savedHint = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
          val savedClip = g.getClip
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
          g.clipRect(r.x + m.left + 1, r.y + 2, r.width - m.left - m.right - 2, r.height - 4)
          g.drawString(text.string, xStart, yStart)
          g.setClip(savedClip)
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)

          val effectColor = effectiveTextAttributes.getEffectColor
          val effectType = effectiveTextAttributes.getEffectType
          if (effectColor != null) {
            g.setColor(effectColor)
            val xEnd = xStart + width
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

          xStart += width
        }
      }
    }
  }

  private def getFontMetrics0(editor: Editor): MyFontMetrics = {
    val familyName = UIManager.getFont("Label.font").getFamily
    val size = Math.max(1, editor.getColorsScheme.getEditorFontSize - 1)
    var metrics = editor.getUserData(HintFontMetrics)
    if (metrics != null && !metrics.isActual(editor, familyName, size)) {
      metrics = null
    }
    if (metrics == null) {
      metrics = new MyFontMetrics(editor, familyName, size)
      editor.putUserData(HintFontMetrics, metrics)
    }
    metrics
  }

  private def getFont(editor: Editor): Font =
    getFontMetrics0(editor).getFont

  def textAt(editor: Editor, x: Int): Option[Text] = {
    val m = getMargin(editor)
    val p = getPadding(editor)
    val fontMetrics = getFontMetrics(editor).getMetrics
    val widths = parts.map(it => fontMetrics.stringWidth(it.string)).scanLeft(m.left + p.left)(_ + _)
    widths.find(_ >= x).map(widths.indexOf).flatMap(i => parts.lift(i - 1)).orElse(parts.headOption)
  }

  def replace(text: Text, replacement: Seq[Text]): Unit = {
    val i = parts.indexOf(text)
    assert(i >= 0, i)
    parts = parts.take(i) ++ replacement ++ parts.drop(i + 1)

    setText(parts.map(_.string).mkString)
  }
}

private object HintRendererExt {
  private final val HintFontMetrics = Key.create[MyFontMetrics]("ParameterHintFontMetrics")

  private final val BackgroundAlpha = 0.55f

  private final val DefaultMargin = new Insets(0, 2, 0, 2)

  private final val DefaultPadding = new Insets(0, 5, 0, 5)
}

