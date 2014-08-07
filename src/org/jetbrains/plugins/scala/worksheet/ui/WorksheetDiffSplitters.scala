package org.jetbrains.plugins.scala
package worksheet.ui

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.diff.impl._
import com.intellij.openapi.diff.impl.highlighting.FragmentSide
import com.intellij.openapi.diff.impl.splitter._
import java.lang.ref.WeakReference
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeList
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.editor.event.{VisibleAreaEvent, VisibleAreaListener}
import javax.swing.JComponent
import java.awt.{Point, Color, Graphics2D, Graphics}
import java.util
import scala.collection.convert.wrapAsJava

/**
 * User: Dmitry.Naydanov
 * Date: 11.04.14.
 */
object WorksheetDiffSplitters {
  private val COLOR1 = Color.GRAY
  private val COLOR2 = Color.LIGHT_GRAY

  def createSimpleSplitter(originalEditor: Editor, viewerEditor: Editor,
                           intervals: Iterable[(Int, Int)], changes: Iterable[(Int, Int)], prop: Float) = {
    new SimpleWorksheetSplitter(originalEditor, viewerEditor, intervals, changes, prop)
  }

  class WorksheetEditingSides(originalEditor: Editor, viewerEditor: Editor) extends EditingSides {
    private val left = new WeakReference(originalEditor)
    private val right = new WeakReference(viewerEditor)

    private lazy val lineBlocks = createLineBlocks(originalEditor.getDocument, viewerEditor.getDocument, originalEditor.getProject)

    override def getEditor(side: FragmentSide) = side match {
      case FragmentSide.SIDE1 => left.get()
      case FragmentSide.SIDE2 => right.get()
    }

    override def getLineBlocks: LineBlocks = lineBlocks
  }


  private def createLineBlocks(original: Document, viewer: Document, project: Project) =
    ChangeList.build(original, viewer, project).getLineBlocks

  private def getVisibleInterval(editor: Editor) = {
    val line = editor.xyToLogicalPosition(new Point(0, editor.getScrollingModel.getVerticalScrollOffset)).line
    (line, editor.getComponent.getHeight / editor.getLineHeight + 1)
  }

  class SimpleWorksheetSplitter(editor1: Editor, editor2: Editor,
                                private var intervals: Iterable[(Int, Int)],
                                private var changes: Iterable[(Int, Int)], prop: Float)
    extends Splitter (false, prop) with DiffSplitterI {
    setDividerWidth(30)
    setFirstComponent(editor1.getComponent)
    setSecondComponent(editor2.getComponent)
    setHonorComponentsMinimumSize(false)

    private val visibleAreaListener = new VisibleAreaListener {
      override def visibleAreaChanged(e: VisibleAreaEvent): Unit = redrawDiffs()
    }

    def getIntervals = intervals

    def getChanges = changes

    def update(newIntervals: Iterable[(Int, Int)], newChanges: Iterable[(Int, Int)]) = {
      intervals = newIntervals
      changes = newChanges
      redrawDiffs()
    }

    override def getComponent: JComponent = this

    override def getVisibleAreaListener: VisibleAreaListener = visibleAreaListener

    override def redrawDiffs(): Unit = getDivider.repaint()

    override def createDivider() = new DividerImpl {
      override def paint(g: Graphics) {
        super.paint(g)
        val width = getWidth
        val height = getHeight
        val editorHeight = editor1.getComponent.getHeight

        val gg = g.create(0, height - editorHeight, width, editorHeight).asInstanceOf[Graphics2D]
        var flag = false

        val (firstVisible1, lastVisible1) = getVisibleInterval(editor1)
        val (firstVisible2, lastVisible2) = getVisibleInterval(editor2)

        val lineHeight1 = editor1.getLineHeight
        val lineHeight2 = editor2.getLineHeight

        val plainPolygons = intervals zip changes collect {
          case ((from, to), (offset, spaces))
            if spaces != 0 && firstVisible1 <= from && lastVisible1 >= to && firstVisible2 <=
              (offset - to + from) && lastVisible2 >= (offset + spaces) =>
            flag = !flag
            new DividerPolygon(
              (from + 1)*lineHeight1,
              (offset - to + from - firstVisible2 + 1)*lineHeight2,
              (to + 1)*lineHeight1,
              (offset + spaces - firstVisible2 + 1)*lineHeight2, if (flag) COLOR1 else COLOR2, false
            )
        }

        DividerPolygon.paintPolygons(new util.ArrayList[DividerPolygon](wrapAsJava asJavaCollection plainPolygons), gg, width)
        gg.dispose()
      }
    }
  }
}
