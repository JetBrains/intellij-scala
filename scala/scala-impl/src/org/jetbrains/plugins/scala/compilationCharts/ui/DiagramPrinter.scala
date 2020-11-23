package org.jetbrains.plugins.scala.compilationCharts.ui

import org.jetbrains.plugins.scala.compilationCharts.Memory
import org.jetbrains.plugins.scala.compilationCharts.ui.Common._
import org.jetbrains.plugins.scala.compiler.CompilationUnitId
import java.awt.{Color, Graphics2D, RenderingHints}
import java.awt.geom.{Line2D, Point2D, Rectangle2D}

import scala.concurrent.duration.{Duration, FiniteDuration}

trait DiagramPrinter {

  def printBackground(graphics: Graphics2D): Unit

  def printDiagram(graphics: Graphics2D): Unit
}

class ProgressDiagramPrinter(clip: Rectangle2D,
                             progressDiagram: ProgressDiagram,
                             currentZoom: Zoom,
                             preferredWidth: Double,
                             darkTheme: Boolean)
  extends DiagramPrinter {

  override def printBackground(graphics: Graphics2D): Unit = {
    graphics.printRect(clip, diagramBackgroundColor)
    Range.inclusive(1, progressDiagram.rowCount).foreach { row =>
      val shift = if (row % 2 == 0) 0 else if (darkTheme) 6 else -13
      val rowColor = diagramBackgroundColor.greyShift(shift)
      val rowY = getRowY(row)
      val rowRect = new Rectangle2D.Double(clip.getX, rowY, clip.getWidth, ProgressRowHeight)
      graphics.printRect(rowRect, rowColor)
    }
    printSegments(graphics, backgroundOnly = true)
  }

  override def printDiagram(graphics: Graphics2D): Unit =
    printSegments(graphics, backgroundOnly = false)

  private def getRowY(row: Int): Double =
    clip.getY + clip.getHeight - ProgressRowHeight * row

  private def printSegments(graphics: Graphics2D,
                            backgroundOnly: Boolean): Unit = {
    def printSegment(graphics: Graphics2D,
                     segment: Segment,
                     row: Int): Unit = {
      val Segment(CompilationUnitId(moduleName, testScope), from, to, progress) = segment
      val x = currentZoom.toPixels(from)
      val segmentClip = new Rectangle2D.Double(
        x,
        getRowY(row) + SegmentGap,
        math.max(currentZoom.toPixels(to) - x - SegmentGap, SegmentGap),
        ProgressRowHeight - SegmentGap
      )
      val color = if (backgroundOnly)
        diagramBackgroundColor
      else if (testScope)
        TestModuleColor
      else
        ProdModuleColor
      graphics.doInClip(segmentClip)(_.printRect(segmentClip, color))
      if (!backgroundOnly) {
        val text = s" $moduleName"
        val textRendering = graphics.getReducedTextRendering(segmentClip, text, NormalFont, HAlign.Left)
        graphics.doInClip(textRendering.rect)(_.printText(textRendering, moduleTextColor(darkTheme)))
        if (progress < 1.0) {
          val textClipX = segmentClip.x + segmentClip.width + BorderStroke.thickness
          val textClip = new Rectangle2D.Double(
            textClipX,
            segmentClip.y,
            preferredWidth - textClipX,
            segmentClip.height
          )
          val text = s" ${(progress * 100).round}%"
          val rendering = graphics.getTextRendering(textClip, text, NormalFont, HAlign.Left, VAlign.Center)
          graphics.doInClip(rendering.rect)(_.printText(rendering, TextColor))
        }
      }
    }

    progressDiagram.segmentGroups.zipWithIndex.foreach { case (group, i) =>
      group.foreach(printSegment(graphics, _, i + 1))
    }
  }

  private def moduleTextColor(darkTheme: Boolean): Color =
    if (darkTheme) DarkModuleTextColor else LightModuleTextColor

  private final val DarkModuleTextColor = new Color(255, 255, 255)
  private final val LightModuleTextColor = TextColor
  private final val SegmentGap = 1
}

class MemoryDiagramPrinter(clip: Rectangle2D,
                           memoryDiagram: MemoryDiagram,
                           currentZoom: Zoom,
                           progressTime: FiniteDuration,
                           darkTheme: Boolean)
  extends DiagramPrinter {

  override def printBackground(graphics: Graphics2D): Unit =
    graphics.printRect(clip, diagramBackgroundColor)

  override def printDiagram(graphics: Graphics2D): Unit = {
    val MemoryDiagram(points, maxMemory) = memoryDiagram

    val leftExtraPoints = getExtraPoints(points.headOption, Duration.Zero, firstPoint = true)
    val rightExtraPoints = getExtraPoints(points.lastOption, progressTime, firstPoint = false)
    val allPoints = leftExtraPoints ++ points ++ rightExtraPoints
    val plotPoints = allPoints.map(toPlotPoint(_, maxMemory))
    val polygon = new Polygon2D(plotPoints)
    graphics.printPolygon(polygon, memoryFillColor(darkTheme))

    if (plotPoints.size >= 2)
      plotPoints.sliding(2).foreach { case Seq((x1, y1), (x2, y2)) =>
        if (x1 != x2)
          graphics.printLine(new Line2D.Double(x1, y1, x2, y2), MemoryLineColor, MemoryLineStroke)
      }
    points.lastOption.foreach { point =>
      val x = currentZoom.toPixels(progressTime)
      val (_, y) = toPlotPoint(point, maxMemory)
      val currentMemoryTextClip = new Rectangle2D.Double(x, y - clip.getHeight, clip.getWidth, 2 * clip.getHeight)
      val text = " " + stringify(point.memory, showMb = true)
      val rendering = {
        val baseRendering = graphics.getTextRendering(currentMemoryTextClip, text, NormalFont, HAlign.Left, VAlign.Center)
        val rect = baseRendering.rect
        lazy val topDelta = clip.getY - rect.getY
        lazy val bottomDelta = clip.getY + clip.getHeight - rect.getY - rect.getHeight
        val deltaY = if (topDelta > 0)
          topDelta
        else if (bottomDelta < 0)
          bottomDelta
        else
          0
        baseRendering.translate(baseRendering.x, baseRendering.y + deltaY)
      }
      graphics.doInClip(rendering.rect)(_.printText(rendering, TextColor))
    }

    val firstMemoryMark = roundMegabytes(maxMemory / 3)
    val lastMemoryMark = firstMemoryMark * 2
    printMemoryMark(graphics, firstMemoryMark, maxMemory, last = false)
    printMemoryMark(graphics, lastMemoryMark, maxMemory, last = true)

    graphics.printBorder(clip, Side.North, LineColor, BorderStroke)
  }

  private def getExtraPoints(edgePoint: Option[MemoryPoint],
                     extraPointTime: FiniteDuration,
                     firstPoint: Boolean): Seq[MemoryPoint] = edgePoint match {
    case Some(MemoryPoint(`extraPointTime`, 0)) =>
      Seq.empty
    case Some(MemoryPoint(`extraPointTime`, _)) | None =>
      Seq(MemoryPoint(extraPointTime, 0L))
    case Some(point) =>
      val extraPoints = Seq(point.copy(time = extraPointTime), MemoryPoint(extraPointTime, 0L))
      if (firstPoint) extraPoints.reverse else extraPoints
  }

  private def toPlotPoint(memoryPoint: MemoryPoint, maxMemory: Memory): (Double, Double) = {
    val MemoryPoint(time, memory) = memoryPoint
    val x = currentZoom.toPixels(time)
    val y = clip.getY + clip.getHeight - (memory.toDouble / maxMemory * clip.getHeight)
    (x, y)
  }

  private def printMemoryMark(graphics: Graphics2D, memory: Memory, maxMemory: Memory, last: Boolean): Unit = {
    val (_, y) = toPlotPoint(MemoryPoint(Duration.Zero, memory), maxMemory)
    val point = new Point2D.Double(clip.getX, y)
    graphics.printHorizontalLine(point, DashLength, LineColor, DashStroke)
    val h = clip.getHeight
    val textClip = new Rectangle2D.Double(clip.getX + DashLength, y - h, clip.getWidth, 2 * h)
    val text = " " + stringify(memory, showMb = last)
    val rendering = graphics.getTextRendering(textClip, text, SmallFont, HAlign.Left, VAlign.Center)
    graphics.printText(rendering, TextColor)
  }

  private def stringify(bytes: Memory, showMb: Boolean): String = {
    val megabytes = toMegabytes(bytes)
    val suffix = if (showMb) " MB" else ""
    s"$megabytes$suffix"
  }

  private def roundMegabytes(bytes: Memory): Memory =
    (toMegabytes(bytes).toDouble / 100).round * 100 * 1024 * 1024

  private def toMegabytes(bytes: Memory): Long =
    math.round(bytes.toDouble / 1024 / 1024)

  private def memoryFillColor(darkTheme: Boolean): Color =
    if (darkTheme) DarkMemoryFillColor else LightMemoryFillColor

  private final val LightMemoryFillColor = new Color(231, 45, 45, 13)
  private final val DarkMemoryFillColor = new Color(231, 45, 45, 26)
}
