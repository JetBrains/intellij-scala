package org.jetbrains.plugins.scala
package worksheet.ui

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{Color, Graphics, Graphics2D, RenderingHints}

import com.intellij.diff.util.DiffDividerDrawUtil.DividerPolygon
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.event.{VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.ui.{Divider, Splitter}
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory

object WorksheetDiffSplitters {

  private val COLOR1 = Color.GRAY
  private val COLOR2 = Color.LIGHT_GRAY

  def createSimpleSplitter(originalEditor: Editor, viewerEditor: Editor, prop: Float): SimpleWorksheetSplitter =
    new SimpleWorksheetSplitter(originalEditor, viewerEditor, Nil, prop)

  class SimpleWorksheetSplitter private[WorksheetDiffSplitters](
    editor1: Editor, editor2: Editor,
    private var mappings: Iterable[DiffMapping],
    prop: Float
  ) extends Splitter (false, prop) {

    private val visibleAreaListener: VisibleAreaListener = (_: VisibleAreaEvent) => redrawDiffs()

    // TODO: improve the way how rendered split diff poligons is tested
    @TestOnly
    def renderedPolygons: Option[Seq[DividerPolygon]] = _renderedPolygons
    private var _renderedPolygons: Option[Seq[DividerPolygon]] = None
    private val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode

    init()

    private def init(): Unit ={
      setDividerWidth(30)
      setFirstComponent(editor1.getComponent)
      setSecondComponent(editor2.getComponent)
      setHonorComponentsMinimumSize(false)

      getDivider.addMouseListener(new MouseAdapter {
        override def mouseReleased(mouseEvent: MouseEvent) {
          val documentManager = PsiDocumentManager.getInstance(editor1.getProject)
          val file = documentManager.getCachedPsiFile(editor1.getDocument)
          file match {
            case file: ScalaFile =>
              WorksheetEditorPrinterFactory.saveOnlyRatio(file, getProportion)
            case _ =>
          }
        }
      })

      editor1.getScrollingModel.addVisibleAreaListener(visibleAreaListener)
    }

    def update(newMappings: Iterable[DiffMapping]): Unit = {
      mappings = newMappings
      redrawDiffs()
    }

    def clear(): Unit = update(Seq())

    def redrawDiffs(): Unit =
      if (!isUnitTestMode) {
        getDivider.repaint()
      } else {
        val polygons = getDivider.asInstanceOf[SimpleWorksheetDivider].generatePolygons
        _renderedPolygons = Some(polygons.toArray.toSeq)
      }

    override def createDivider(): Divider = new SimpleWorksheetDivider()

    private class SimpleWorksheetDivider extends DividerImpl {

      @Measure
      override def paint(g: Graphics): Unit = {
        super.paint(g)
        if (mappings.isEmpty) return

        val width = getWidth
        val height = getHeight
        val editorHeight = editor1.getComponent.getHeight

        val gg = g.create(0, height - editorHeight, width, editorHeight).asInstanceOf[Graphics2D]
        val rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        gg.setRenderingHints(rh)

        val polygons = generatePolygons

        for (polygon <- polygons) {
          polygon.paint(gg, width, true)
        }

        gg.dispose()
      }

      def generatePolygons: Iterable[DividerPolygon] = {
        // returns mappings of left lines to right lines
        def diffToLinesMapping(diff: DiffMapping): (Segment, Segment) = {
          val DiffMapping(from, to, offset, spaces) = diff
          val start1 = from
          val end1   = to + 1
          val start2 = offset - (to - from)
          val end2   = offset + spaces + 1
          Segment(start1, end1) -> Segment(start2, end2)
        }

        val lineHeight1 = editor1.getLineHeight
        val lineHeight2 = editor2.getLineHeight

        // returns mappings of left offsets to right offsets (in pixels)
        // (relative to the beginning of the document, not screen)
        def linesToOffsetMapping(tuple: (Segment, Segment)): (Segment, Segment) =
          tuple._1 * lineHeight1 -> tuple._2 * lineHeight2

        // ranges for both editors can differ if  font sizes differ, but usually are equal cause font size is the same
        val visibleInterval1 = calcVisibleInterval(editor1)
        val visibleInterval2 = calcVisibleInterval(editor2)

        val offsetMappings = mappings.map(diffToLinesMapping).map(linesToOffsetMapping)
        val plainPolygons = offsetMappings.zipWithIndex.collect {
          case ((seg1, seg2), idx)
            if visibleInterval1.intersects(seg1) || visibleInterval2.intersects(seg2) =>

            // relative to the beginning of the editor component window top
            val start1 = seg1.start - visibleInterval1.start
            val end1   = seg1.end - visibleInterval1.start
            val start2 = seg2.start - visibleInterval2.start
            val end2   = seg2.end - visibleInterval2.start

            val isEven = (idx & 1) == 0
            // to switch color between sibling polygons, to make them more noticeable
            val fillColor = if (isEven) COLOR1 else COLOR2

            new DividerPolygon(start1, start2, end1, end2, fillColor, null, true)
        }
        plainPolygons
      }
    }

    /**
     * @return first and last visible line offsets in pixels (from top of the editor)
     *         visible means that it it doesnt care about folded lines
     */
    private def calcVisibleInterval(editor: Editor): Segment = {
      if (!isUnitTestMode) {
        val verticalScrollOffset = editor.getScrollingModel.getVerticalScrollOffset
        val editorHeight = editor.getComponent.getHeight
        val first = verticalScrollOffset
        val last = verticalScrollOffset + editorHeight
        Segment(first, last)
      } else {
        Segment(0, 100500)
      }
    }
  }

  /**
   * @param leftStartLine       (inclusive)
   * @param leftEndLine         (inclusive)
   * @param rightFoldLineOffset (visible)
   */
  case class DiffMapping(leftStartLine: Int,
                         leftEndLine: Int,
                         rightFoldLineOffset: Int,
                         rightFoldedLinesCount: Int)

  /**
   * @param start (inclusive)
   * @param end (inclusive)
   */
  private case class Segment(start: Int, end: Int) {
    def contains(point: Int): Boolean = start <= point && point <= end
    def contains(other: Segment): Boolean = start <= other.start && other.end <= end
    def intersects(other: Segment): Boolean = start <= other.end && other.start <= end
    def * (a: Int): Segment = Segment(start * a, end * a)
  }
}
