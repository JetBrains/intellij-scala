package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.ide.ui.{AntialiasingType, UISettingsUtils}
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.paint.EffectPainter
import com.intellij.util.ui.{GraphicsUtil, JBUI}
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.annotator.hints.{Corners, Text}
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer._
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.awt._
import java.awt.geom.Path2D

//TODO: why it's in "implicits" package?
// It's also used in methodChains, rangeHints
// We should move it to a proper package
class TextPartsHintRenderer(var parts: Seq[Text], menuProvider: MenuProvider, corners: Corners = Corners.All)
  extends HintRendererProxy(parts.map(_.string).mkString) {

  private val originalParts = parts

  protected override def getContextMenuGroupId0(editor: Editor): String = menuProvider.groupIdOrNull

  protected override def getContextMenuGroup0(editor: Editor): ActionGroup = menuProvider.actionGroupOrNull

  protected def getMargin(editor: Editor): Insets = DefaultMargin

  protected def getPadding(editor: Editor): Insets = DefaultPadding

  protected override def calcWidthInPixels0(editor: Editor): Int = {
    val m = getMargin(editor)
    val p = getPadding(editor)
    val metrics = getFontMetrics0(editor).getMetrics
    metrics.stringWidth(getText) + m.left + p.left + p.right + m.right
  }

  protected def adjustedBoxStart(r: Rectangle, m: Insets): Int = r.x + m.left

  override def paint0(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
    if (!editor.is[EditorImpl]) return
    val editorImpl = editor.asInstanceOf[EditorImpl]

    val m = getMargin(editor)
    val p = getPadding(editor)

    val ascent = editorImpl.getAscent
    val descent = editorImpl.getDescent
    val g2d = g.asInstanceOf[Graphics2D]

    val attributes = getTextAttributes(editor)
    if (attributes != null) {
      val fontMetrics = getFontMetrics0(editor)

      val widthMinusMargins = r.width - m.left - m.right
      val xPlusLeftMargin = adjustedBoxStart(r, m)

      val backgroundColor = attributes.getBackgroundColor
      if (backgroundColor != null) {
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BackgroundAlpha)
        g.setColor(backgroundColor)
        if (p.left == 0 && p.right == 0) {
          if (RoundCorners) {
            fillRoundRect(g2d, xPlusLeftMargin, r.y, widthMinusMargins, r.height, roundCorners = corners, ArcWidth, ArcHeight)
          } else {
            g.fillRect(xPlusLeftMargin, r.y, widthMinusMargins, r.height)
          }
        } else {
          g.fillRoundRect(xPlusLeftMargin, r.y, widthMinusMargins, r.height, ArcWidth, ArcHeight)
        }
        config.restore()
      }

      val foregroundColor = attributes.getForegroundColor
      if (foregroundColor != null) {
        val metrics = fontMetrics.getMetrics
        var xStart = xPlusLeftMargin + p.left
        val yStart = r.y + Math.max(ascent, (r.height + metrics.getAscent - metrics.getDescent) / 2)

        parts.foreach { text =>
          val width = metrics.stringWidth(text.string)

          val effectiveTextAttributes = text.effective(editor, attributes)

          val backgroundColor = effectiveTextAttributes.getBackgroundColor
          if (backgroundColor != null) {
            val config = GraphicsUtil.setupAAPainting(g)
            GraphicsUtil.paintWithAlpha(g, BackgroundAlpha)
            g.setColor(backgroundColor)
            if (RoundCorners) {
              val roundCorners = {
                val left = if (text.eq(parts.head)) corners.intersect(Corners.Left) else Corners.None
                val right = if (text.eq(parts.last)) corners.intersect(Corners.Right) else Corners.None
                left.union(right)
              }
              fillRoundRect(g2d, xStart, r.y, width, r.height, roundCorners, ArcWidth, ArcHeight)
            } else {
              g.fillRect(xStart, r.y, width, r.height)
            }
            config.restore()
          }

          val foregroundColor = effectiveTextAttributes.getForegroundColor
          g.setColor(foregroundColor)

          g.setFont(editor.getColorsScheme.getFont(editorFontTypeOf(effectiveTextAttributes.getFontType)))

          val savedHint = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
          val savedClip = g.getClip
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
          g.clipRect(xPlusLeftMargin, r.y, widthMinusMargins, r.height)
          g.drawString(text.string, xStart, yStart)
          g.setClip(savedClip)
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)

          val effectColor = effectiveTextAttributes.getEffectColor
          val effectType = effectiveTextAttributes.getEffectType
          if (effectColor != null) {
            g.setColor(effectColor)
            val (x1, x2) = {
              val (beginOffset, endOffset) = text.effectRange.getOrElse(0, text.string.length)
              val (leftMargin, rightMargin) = (metrics.stringWidth(text.string.take(beginOffset)),
                metrics.stringWidth(text.string.takeRight(text.string.length - endOffset)))
              (xStart + leftMargin, xStart + width - rightMargin)
            }
            val y = r.y + ascent
            val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)

            effectType match {
              case EffectType.LINE_UNDERSCORE => EffectPainter.LINE_UNDERSCORE.paint(g2d, x1, y, x2 - x1, descent, font)
              case EffectType.BOLD_LINE_UNDERSCORE => EffectPainter.BOLD_LINE_UNDERSCORE.paint(g2d, x1, y, x2 - x1, descent, font)
              case EffectType.STRIKEOUT => EffectPainter.STRIKE_THROUGH.paint(g2d, x1, y, x2 - x1, editorImpl.getCharHeight, font)
              case EffectType.WAVE_UNDERSCORE => EffectPainter.WAVE_UNDERSCORE.paint(g2d, x1, y, x2 - x1, descent, font)
              case EffectType.BOLD_DOTTED_LINE => EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g2d, x1, y, x2 - x1, descent, font)
              case _ =>
            }
          }

          xStart += width
        }
      }
    }
  }

  private def fillRoundRect(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, roundCorners: Corners, arcWidth: Int, arcHeight: Int): Unit = {
    import roundCorners._

    val path = new Path2D.Double()
    path.moveTo(if (topLeft) arcWidth else 0, 0)
    path.lineTo(if (topRight) width - arcWidth else width, 0)
    if (topRight) path.curveTo(width, 0, width, 0, width, arcHeight)
    path.lineTo(width, if (bottomRight) height - arcHeight else height)
    if (bottomRight) path.curveTo(width, height, width, height, width - arcWidth, height)
    path.lineTo(if (bottomLeft) arcWidth else 0, height)
    if (bottomLeft) path.curveTo(0, height, 0, height, 0, height - arcHeight)
    path.lineTo(0, if (topLeft) arcHeight else 0)
    if (topLeft) path.curveTo(0, 0, 0, 0, arcWidth, 0)
    path.closePath()

    val transform = g.getTransform

    g.translate(x, y)
    g.fill(path)

    g.setTransform(transform)
  }

  private def editorFontTypeOf(fontType: Int) = fontType match {
    case Font.BOLD => EditorFontType.BOLD
    case Font.ITALIC => EditorFontType.ITALIC
    case _ => EditorFontType.PLAIN
  }

  private def getFontMetrics0(editor: Editor): MyFontMetrics = {
    val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)
    var metrics = editor.getUserData(HintFontMetrics)
    if (metrics != null && !metrics.isActual(editor, font.getFamily, font.getSize)) {
      metrics = null
    }
    if (metrics == null) {
      metrics = new MyFontMetrics(editor, font.getFamily, font.getSize)
      editor.putUserData(HintFontMetrics, metrics)
    }
    metrics
  }

  def textAt(editor: Editor, x: Int): Option[Text] = {
    val m = getMargin(editor)
    val p = getPadding(editor)
    val fontMetrics = getFontMetrics0(editor).getMetrics
    val xs = parts.map(it => fontMetrics.stringWidth(it.string)).scanLeft(m.left + p.left)(_ + _)
    parts.zip(xs.zip(xs.tail)).collectFirst {
      case (text, (start, end)) if start <= x && x <= end => text
    }
  }

  def expand(text: Text): Unit = {
    text.expansion.foreach(it => replace(text, it()))
  }

  private def replace(text: Text, replacement: Seq[Text]): Unit = {
    val i = parts.indexOf(text)
    assert(i >= 0, i)
    parts = parts.take(i) ++ replacement ++ parts.drop(i + 1)

    setText(parts.map(_.string).mkString)
  }

  def expand(level: Int = ExpansionLevel): Unit = if (level >= 1) {
    var expanded = false

    parts.foreach { text =>
      text.expansion.foreach { expansion =>
        replace(text, expansion())
        expanded = true
      }
    }

    if (expanded) {
      expand(level - 1)
    }
  }

  def expanded: Boolean = originalParts != parts

  def collapse(): Unit = {
    parts = originalParts
    setText(parts.map(_.string).mkString)
  }
}

private object TextPartsHintRenderer {
  private final val HintFontMetrics = Key.create[MyFontMetrics]("SCALA_IMPLICIT_HINT_FONT_METRICS")

  private final val BackgroundAlpha = 0.55F

  private final val DefaultMargin = JBUI.emptyInsets()

  private final val DefaultPadding = JBUI.emptyInsets()

  private def RoundCorners = Registry.is("scala.round.hint.corners")

  private def ArcWidth = (8 * UISettingsUtils.getInstance.getCurrentIdeScale).round

  private def ArcHeight = (8 * UISettingsUtils.getInstance.getCurrentIdeScale).round

  private final val ExpansionLevel = 5
}
