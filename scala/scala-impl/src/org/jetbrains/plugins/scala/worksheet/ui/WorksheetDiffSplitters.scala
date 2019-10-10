package org.jetbrains.plugins.scala
package worksheet.ui

import java.awt._
import java.awt.event.{MouseAdapter, MouseEvent}

import com.intellij.diff.util.DiffDividerDrawUtil.DividerPolygon
import com.intellij.openapi.diff.impl._
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.event.{VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.ui.Splitter
import com.intellij.psi.PsiDocumentManager
import javax.swing.JComponent
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Dmitry.Naydanov
 * Date: 11.04.14.
 */
object WorksheetDiffSplitters {

  private val COLOR1 = Color.GRAY
  private val COLOR2 = Color.LIGHT_GRAY

  def createSimpleSplitter(originalEditor: Editor, viewerEditor: Editor,
                           intervals: Iterable[(Int, Int)], changes: Iterable[(Int, Int)],
                           prop: Float): SimpleWorksheetSplitter = {
    new SimpleWorksheetSplitter(originalEditor, viewerEditor, intervals, changes, prop)
  }

  private def getVisibleInterval(editor: Editor) = {
    val line = editor.xyToLogicalPosition(new Point(0, editor.getScrollingModel.getVerticalScrollOffset)).line
    (line, editor.getComponent.getHeight / editor.getLineHeight + 1)
  }

  class SimpleWorksheetSplitter private[WorksheetDiffSplitters](
    editor1: Editor, editor2: Editor,
    private var intervals: Iterable[(Int, Int)],
    private var changes: Iterable[(Int, Int)], prop: Float
  ) extends Splitter (false, prop) {

    private val visibleAreaListener: VisibleAreaListener = (e: VisibleAreaEvent) => redrawDiffs()

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

      editor1.getScrollingModel.addVisibleAreaListener(getVisibleAreaListener)
    }

    def getIntervals: Iterable[(Int, Int)] = intervals

    def getChanges: Iterable[(Int, Int)] = changes

    def update(newIntervals: Iterable[(Int, Int)], newChanges: Iterable[(Int, Int)]): Unit = {
      intervals = newIntervals
      changes = newChanges
      redrawDiffs()
    }

    def getComponent: JComponent = this

    def getVisibleAreaListener: VisibleAreaListener = visibleAreaListener

    def redrawDiffs(): Unit = getDivider.repaint()

    // TODO: extract to a separate class
    override def createDivider(): DividerImpl = new DividerImpl {

      override def paint(g: Graphics) {
        super.paint(g)

        val width = getWidth
        val height = getHeight
        val editorHeight = editor1.getComponent.getHeight

        val gg = g.create(0, height - editorHeight, width, editorHeight).asInstanceOf[Graphics2D]

        // to switch color between sibling polygons, to make them more noticeable
        var colorFlag = false

        val (firstVisible1, lastVisible1) = getVisibleInterval(editor1)
        val (firstVisible2, lastVisible2) = getVisibleInterval(editor2)

        val lineHeight1 = editor1.getLineHeight
        val lineHeight2 = editor2.getLineHeight

        val zipped = intervals.zip(changes)
        val plainPolygons = zipped.collect {
          case ((from, to), (offset, spaces))
            if spaces != 0 &&
              firstVisible1 <= from && to <= lastVisible1 &&
              firstVisible2 <= (from + offset - to) && (offset + spaces) <= lastVisible2 =>

            colorFlag = !colorFlag

            val startLine1 = from - firstVisible1 + 1
            val startLine2 = offset + from - to - firstVisible2 + 1
            val endLine1   = to - firstVisible1 + 1
            val endLine2   = offset + spaces - firstVisible2 + 1

            val start1 = startLine1 * lineHeight1
            val start2 = startLine2 * lineHeight2
            val end1   = endLine1 * lineHeight1
            val end2   = endLine2 * lineHeight2

            val fillColor = if (colorFlag) COLOR1 else COLOR2

            new DividerPolygon(start1, start2, end1, end2, fillColor, null, true)
        }

        for (polygon <- plainPolygons) {
          polygon.paint(gg, width, true)
        }
        gg.dispose()
      }
    }
  }
}
