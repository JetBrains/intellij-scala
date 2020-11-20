package org.jetbrains.plugins.scala.compilationCharts.ui

import java.awt.geom.{Point2D, Rectangle2D}
import java.awt.{Graphics, Graphics2D, Point}
import com.intellij.ide.ui.{LafManager, UISettings}
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import org.jetbrains.plugins.scala.compilationCharts.{CompilationProgressStateManager, CompileServerMetricsStateManager, Memory}
import Common._
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt

import javax.swing.JViewport
import org.jetbrains.plugins.scala.extensions.invokeLater

import scala.concurrent.duration.{Duration, FiniteDuration}

class DiagramsComponent(project: Project,
                        defaultZoom: Zoom)
  extends JBPanelWithEmptyText {

  import DiagramsComponent._

  private var currentZoom = defaultZoom

  def setZoom(zoom: Zoom): Unit = {
    val viewport = getParent.asInstanceOf[JViewport]
    val viewRect = viewport.getViewRect
    val centerDuration = currentZoom.fromPixels(viewRect.getX + viewRect.getWidth / 2)

    currentZoom = zoom
    CompilationChartsBuildToolWindowNodeFactory.refresh(project)

    val newViewPosition = new Point(
      (currentZoom.toPixels(centerDuration) - viewRect.getWidth / 2).round.toInt,
      viewRect.y
    )
    // It works only with two invocations. IDK why ¯\_(ツ)_/¯
    viewport.setViewPosition(newViewPosition)
    invokeLater(viewport.setViewPosition(newViewPosition))
  }

  override def paintComponent(g: Graphics): Unit = {
    val graphics = g.asInstanceOf[Graphics2D]
    UISettings.setupAntialiasing(graphics)
    val clipBounds = g.getClipBounds
    val darkTheme = isDarkTheme

    val progressState = CompilationProgressStateManager.get(project)
    val metricsState = CompileServerMetricsStateManager.get(project)

    val diagrams = Diagrams.calculate(progressState, metricsState)
    val Diagrams(progressDiagram, memoryDiagram, progressTime) = diagrams
    val preferredWidth = math.max(
      currentZoom.toPixels(progressTime + durationAhead),
      clipBounds.width
    )
    val clips = getDiagramClips(clipBounds, progressDiagram.rowCount)

    val diagramPrinters = Seq(
      new ProgressDiagramPrinter(clips.progressDiagram, progressDiagram, currentZoom, preferredWidth, darkTheme),
      new MemoryDiagramPrinter(clips.memoryDiagram, memoryDiagram, currentZoom, progressTime, darkTheme)
    )

    diagramPrinters.foreach(_.printBackground(graphics))
    printDiagramVerticalLines(graphics, clips.allDiagramsClip, preferredWidth, progressTime)
    diagramPrinters.foreach(_.printDiagram(graphics))
    printDurationAxis(graphics, clips.durationAxis, preferredWidth)

    val preferredSize = {
      val preferredHeight = clips.durationAxis.getY + clips.durationAxis.getHeight - clips.progressDiagram.getY
      new Rectangle2D.Double(0, 0, preferredWidth, preferredHeight).getBounds.getSize
    }
    setPreferredSize(preferredSize)
    revalidate()
  }

  private def getDiagramClips(clipBounds: Rectangle2D, progressDiagramRowCount: Int): DiagramClips = {
    def nextClip(height: Double, prevClip: Rectangle2D): Rectangle2D.Double =
      new Rectangle2D.Double(clipBounds.getX, prevClip.getY + prevClip.getHeight, clipBounds.getWidth, height)

    val progressDiagramHeight = progressDiagramRowCount * ProgressRowHeight
    val progressDiagramClip = new Rectangle2D.Double(clipBounds.getX, 0, clipBounds.getWidth, progressDiagramHeight)
    val memoryDiagramY = progressDiagramClip.y + progressDiagramClip.height
    val memoryDiagramHeight = math.max(
      clipBounds.getHeight - memoryDiagramY - DurationAxisHeight,
      MinMemoryDiagramHeight
    )
    val memoryDiagramClip = nextClip(memoryDiagramHeight, progressDiagramClip)
    val durationAxisClip = nextClip(DurationAxisHeight, memoryDiagramClip)
    DiagramClips(
      progressDiagram = progressDiagramClip,
      memoryDiagram = memoryDiagramClip,
      durationAxis = durationAxisClip
    )
  }

  private def printDurationAxis(graphics: Graphics2D, clip: Rectangle2D, preferredWidth: Double): Unit = {
    graphics.printRect(clip, DiagramBackgroundColor)
    printTopBorder(graphics, clip)
    durationXIterator(preferredWidth).zipWithIndex.foreach { case (x, i) =>
      val point = new Point2D.Double(x, clip.getY)
      if (i % currentZoom.durationLabelPeriod == 0) {
        graphics.printVerticalLine(point, LongDashLength, LineColor, DashStroke)
        val text = " " + stringify(i * currentZoom.durationStep)
        val textClip = new Rectangle2D.Double(point.x, clip.getY, clip.getWidth, clip.getHeight)
        val rendering = graphics.getTextRendering(textClip, text, SmallFont, HAlign.Left, VAlign.Top)
        val fixedRendering = rendering.translate(rendering.x, rendering.y + rendering.rect.getHeight / 4)
        graphics.doInClip(fixedRendering.rect)(_.printText(fixedRendering, TextColor))
      } else {
        graphics.printVerticalLine(point, DashLength, LineColor, DashStroke)
      }
    }
  }

  private def printDiagramVerticalLines(graphics: Graphics2D,
                                        clip: Rectangle2D,
                                        preferredWidth: Double,
                                        progressTime: FiniteDuration): Unit = {
    durationXIterator(preferredWidth).zipWithIndex.foreach { case (x, i) =>
      if (i != 0 && i % currentZoom.durationLabelPeriod == 0) {
        val point = new Point2D.Double(x, clip.getY)
        graphics.printVerticalLine(point, clip.getHeight, LineColor, DashedStroke)
      }
    }
    val progressLinePoint = currentZoom.toPixels(progressTime)
    val linePoint = new Point2D.Double(progressLinePoint, clip.getY)
    graphics.printVerticalLine(linePoint, clip.getHeight, TextColor, ProgressLineStroke)
  }

  private def durationXIterator(preferredWidth: Double): Iterator[Double] = {
    val zero = currentZoom.toPixels(Duration.Zero)
    val step = currentZoom.toPixels(currentZoom.durationStep)
    Iterator.iterate(zero)(_ + step).takeWhile(_ <= preferredWidth)
  }

  private def durationAhead: FiniteDuration =
    currentZoom.durationStep * currentZoom.durationLabelPeriod
}

object DiagramsComponent {

  private final case class DiagramClips(progressDiagram: Rectangle2D,
                                        memoryDiagram: Rectangle2D,
                                        durationAxis: Rectangle2D) {
    def allDiagramsClip: Rectangle2D = new Rectangle2D.Double(
      progressDiagram.getX,
      progressDiagram.getY,
      progressDiagram.getWidth,
      memoryDiagram.getY + memoryDiagram.getHeight,
    )
  }

  private def isDarkTheme: Boolean =
    Option(LafManager.getInstance.getCurrentLookAndFeel)
      .flatMap(_.asOptionOf[UIThemeBasedLookAndFeelInfo])
      .exists(_.getTheme.isDark) || StartupUiUtil.isUnderDarcula

  def stringify(bytes: Memory, showMb: Boolean): String = {
    val megabytes = toMegabytes(bytes)
    val suffix = if (showMb) " MB" else ""
    s"$megabytes$suffix"
  }

  def smartRound(bytes: Memory): Memory =
    (toMegabytes(bytes).toDouble / 100).round * 100 * 1024 * 1024

  private def toMegabytes(bytes: Memory): Long =
    math.round(bytes.toDouble / 1024 / 1024)

  private def stringify(duration: FiniteDuration): String = {
    val minutes = duration.toMinutes
    val seconds = duration.toSeconds % 60
    val minutesStr = Option(minutes).filter(_ > 0).map(_.toString + "m")
    val secondsStr = Option(seconds).filter(_ > 0).map(_.toString + "s")
    val result = Seq(minutesStr, secondsStr).flatten.mkString(" ")
    if (result.nonEmpty) result else "0"
  }

  private final val MinMemoryDiagramHeight = ProgressRowHeight * 3
  private final val DurationAxisHeight = ProgressRowHeight
  private final val LongDashLength = DashLength * 2

  private final val DashedStroke = new LineStroke(DashStroke.thickness, dashLength = Some((ProgressRowHeight / 5).toFloat))
}
