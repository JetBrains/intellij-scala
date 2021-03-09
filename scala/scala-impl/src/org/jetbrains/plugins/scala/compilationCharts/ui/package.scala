package org.jetbrains.plugins.scala.compilationCharts

import java.awt.geom.{Line2D, Point2D, Rectangle2D}
import java.awt.{Color, Font, Graphics, Graphics2D, Rectangle}
import scala.annotation.tailrec
import scala.util.Using
import scala.util.Using.{Releasable, resource}

package object ui {

  implicit object GraphicsReleasable extends Releasable[Graphics] {
    override def release(resource: Graphics): Unit = resource.dispose()
  }

  implicit class GraphicsExt(private val graphics: Graphics2D) extends AnyVal {

    def doInClip[A](clip: Rectangle2D)
                   (action: Graphics2D => A): A =
      Using.resource(graphics.create().asInstanceOf[Graphics2D]) { clipGraphics =>
        clipGraphics.setClip(clip createIntersection graphics.getClipBounds)
        action(clipGraphics)
      }

    def printPolygon(polygon: Polygon2D, color: Color): Unit = {
      graphics.setColor(color)
      graphics.fill(polygon.toPath2D)
    }

    def printRect(rect: Rectangle2D, color: Color): Unit = {
      graphics.setColor(color)
      graphics.fill(rect)
    }

    def getTextRendering(clip: Rectangle2D, text: String, font: Font, hAlign: HAlign, vAlign: VAlign): TextRendering =
      TextRendering.calculate(clip, graphics, text, font, hAlign, vAlign)

    def getReducedTextRendering(clip: Rectangle2D, text: String, font: Font, hAlign: HAlign): TextRendering = {
      val fontMetrics = graphics.getFontMetrics(graphics.getFont)

      @tailrec
      def rec(fragment: String, isFull: Boolean): TextRendering = if (fragment.nonEmpty) {
        val toPrint = if (isFull) fragment else s"$fragment…"
        val stringBounds = fontMetrics.getStringBounds(toPrint, graphics)
        if (stringBounds.getWidth <= clip.getWidth)
          graphics.getTextRendering(clip, toPrint, font, hAlign = hAlign, VAlign.Center)
        else
          rec(fragment.init, isFull = false)
      } else {
        TextRendering.Empty
      }

      rec(text, isFull = true)
    }

    def printText(rendering: TextRendering, color: Color): Unit = if (rendering.text.nonEmpty) {
      graphics.setColor(color)
      graphics.setFont(rendering.font)
      graphics.drawString(rendering.text, rendering.x.toFloat, rendering.y.toFloat)
    }

    def printLine(line: Line2D, color: Color, stroke: LineStroke): Unit = {
      val oldStroke = graphics.getStroke
      graphics.setStroke(stroke.toStroke)
      graphics.setColor(color)
      graphics.draw(line)
      graphics.setStroke(oldStroke)
    }

    def printVerticalLine(point: Point2D, length: Double, color: Color, stroke: LineStroke): Unit =
      printVerticalOrHorizontalLine(point, length, color, stroke, vertical = true)

    def printHorizontalLine(point: Point2D, length: Double, color: Color, stroke: LineStroke): Unit =
      printVerticalOrHorizontalLine(point, length, color, stroke, vertical = false)

    private def printVerticalOrHorizontalLine(point: Point2D,
                                              length: Double,
                                              color: Color,
                                              stroke: LineStroke,
                                              vertical: Boolean): Unit = {
      val x1 = point.getX
      val y1 = point.getY
      val x2 = if (vertical) x1 else x1 + length
      val y2 = if (vertical) y1 + length else y1
      val line = new Line2D.Double(x1, y1, x2, y2)

      printLine(line, color, stroke)
    }

    def printBorder(bounds: Rectangle2D, side: Side, color: Color, stroke: LineStroke): Unit = {
      val offset = stroke.thickness / 2
      val x = side match {
        case Side.North | Side.South => bounds.getX
        case Side.West => bounds.getX + offset
        case Side.East => bounds.getX + bounds.getWidth - offset
      }
      val y = side match {
        case Side.North => bounds.getY + offset
        case Side.South => bounds.getY + bounds.getHeight - offset
        case Side.West | Side.East => bounds.getY
      }
      val length = side match {
        case Side.North | Side.South => bounds.getWidth
        case Side.East | Side.West => bounds.getHeight
      }
      val vertical = side match {
        case Side.North | Side.South => false
        case Side.West | Side.East => true
      }
      printVerticalOrHorizontalLine(new Point2D.Double(x, y), length, color, stroke, vertical)
    }
  }

  implicit class ColorExt(private val color: Color) extends AnyVal {
    def greyShift(value: Int): Color = {
      def shiftComponent(component: Int): Int = {
        val result = component + value
        if (result < 0) 0 else if (result > 255) 255 else result
      }
      new Color(shiftComponent(color.getRed), shiftComponent(color.getGreen), shiftComponent(color.getBlue))
    }
  }

  private[ui] implicit val colorOrdering: Ordering[Level] = Ordering.by((_: Level).ordinal)
}
