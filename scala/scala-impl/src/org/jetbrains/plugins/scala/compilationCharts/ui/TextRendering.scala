package org.jetbrains.plugins.scala.compilationCharts.ui

import java.awt.geom.Rectangle2D
import java.awt.{Font, Graphics2D}

import com.intellij.ui.components.JBLabel

class TextRendering private(val text: String,
                            val font: Font,
                            val x: Double,
                            val y: Double,
                            width: Double,
                            height: Double,
                            ascent: Double,
                            descent: Double) {

  def translate(x: Double, y: Double): TextRendering = new TextRendering(
    text = text,
    font = font,
    x = x,
    y = y,
    width = width,
    height = height,
    ascent = ascent,
    descent = descent
  )

  def rect: Rectangle2D = new Rectangle2D.Double(
    x,
    y - height + ascent,
    width,
    height - ascent + descent
  )
}

object TextRendering {

  def calculate(clip: Rectangle2D,
                graphics: Graphics2D,
                text: String,
                font: Font,
                hAlign: HAlign,
                vAlign: VAlign): TextRendering = {
    graphics.setFont(font)
    val fontMetrics = graphics.getFontMetrics(graphics.getFont)
    val stringBounds = fontMetrics.getStringBounds(text, graphics)
    val ascent = fontMetrics.getAscent.toDouble * 0.33 // The returned ascent value is incorrect. IDK why.
    val descent = fontMetrics.getDescent
    val deltaX = hAlign match {
      case HAlign.Center => clip.getWidth / 2 - stringBounds.getWidth / 2
      case HAlign.Left => 0
      case HAlign.Right => clip.getWidth - stringBounds.getWidth
    }
    val deltaY = vAlign match {
      case VAlign.Center => clip.getHeight / 2 + ascent
      case VAlign.Bottom => clip.getHeight - descent
      case VAlign.Top => stringBounds.getHeight - ascent
    }
    val x = clip.getX + deltaX
    val y = clip.getY + deltaY
    new TextRendering(
      text = text,
      font = font,
      x = x,
      y = y,
      width = stringBounds.getWidth,
      height = stringBounds.getHeight,
      ascent = ascent,
      descent = descent
    )
  }

  final val Empty = new TextRendering(
    text = "",
    font = new JBLabel().getFont,
    x = 0.0,
    y = 0.0,
    width = 0.0,
    height = 0.0,
    ascent = 0,
    descent = 0
  )
}
