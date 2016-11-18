package org.jetbrains.plugins.scala
package worksheet.ui

import java.awt._
import java.awt.event.{MouseAdapter, MouseEvent}
import java.lang.ref.WeakReference
import java.util
import javax.swing.JComponent

import com.intellij.openapi.diff.impl._
import com.intellij.openapi.diff.impl.highlighting.FragmentSide
import com.intellij.openapi.diff.impl.incrementalMerge._
import com.intellij.openapi.diff.impl.splitter._
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.event.{VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.convert.wrapAsJava
import scala.collection.mutable

/**
 * User: Dmitry.Naydanov
 * Date: 11.04.14.
 */
object WorksheetDiffSplitters {
  private val COLOR1 = Color.GRAY
  private val COLOR2 = Color.LIGHT_GRAY

  def createSimpleSplitter(originalEditor: Editor, viewerEditor: Editor,
                           intervals: Iterable[(Int, Int)], changes: Iterable[(Int, Int)], prop: Float): SimpleWorksheetSplitter = {
    new SimpleWorksheetSplitter(originalEditor, viewerEditor, intervals, changes, prop)
  }

  class WorksheetEditingSides(originalEditor: Editor, viewerEditor: Editor, foldInfo: Option[WorksheetFoldGroup] = None) extends EditingSides {
    private val left = new WeakReference(originalEditor)
    private val right = new WeakReference(viewerEditor)

    private lazy val lineBlocks = createLineBlocks(originalEditor.getDocument, viewerEditor.getDocument.getLineCount, originalEditor.getProject)

    override def getEditor(side: FragmentSide): Editor = side match {
      case FragmentSide.SIDE1 => left.get()
      case FragmentSide.SIDE2 => right.get()
    }

    override def getLineBlocks: LineBlocks = lineBlocks
  }


  private def createLineBlocks(original: Document, viewerSize: Int, project: Project) = {
    val originalSize = original.getLineCount
    val minSize = Math.min(originalSize, viewerSize)
    
    val originalFake = StringBuilder.newBuilder
    val viewerFake = StringBuilder.newBuilder
    val random = new java.util.Random
    
    for (i <- 0 until minSize) {
      val line = random.nextInt().toString
      
      originalFake.append(line).append("\n")
      viewerFake.append(if (i % 2 == 0) line else random.nextInt.toString).append("\n")
    }
    
    if (originalSize > viewerSize) 
      viewerFake.append(StringUtil.repeat("_\n", originalSize - viewerSize))
    else if (originalSize < viewerSize) 
      originalFake.append(StringUtil.repeat("_\n", viewerSize - originalSize))

    val factory = EditorFactory.getInstance

    val originalDoc = factory.createDocument(originalFake.toString())
    val viewerDoc = factory.createDocument(viewerFake.toString())

    ChangeList.build(originalDoc, viewerDoc, project).getLineBlocks
  }

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

    getDivider.addMouseListener(new MouseAdapter {
      override def mouseReleased(mouseEvent: MouseEvent) {
        val f = getProportion

        Option(PsiDocumentManager.getInstance(editor1.getProject) getCachedPsiFile editor1.getDocument) foreach {
          case file: ScalaFile =>
            WorksheetEditorPrinter.saveOnlyRatio(file, f)
          case _ =>
        }
      }
    })

    private val visibleAreaListener = new VisibleAreaListener {
      override def visibleAreaChanged(e: VisibleAreaEvent): Unit = redrawDiffs()
    }
    
    editor1.getScrollingModel.addVisibleAreaListener(getVisibleAreaListener)

    def getIntervals: Iterable[(Int, Int)] = intervals

    def getChanges: Iterable[(Int, Int)] = changes

    def update(newIntervals: Iterable[(Int, Int)], newChanges: Iterable[(Int, Int)]): Unit = {
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
