package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.diff.util.DiffDividerDrawUtil.DividerPolygon
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.{VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.fileEditor.impl.text.TextEditorComponent
import com.intellij.openapi.ui.{Divider, Splitter}
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{Component, Graphics, Graphics2D, GridBagConstraints, GridBagLayout, RenderingHints}
import javax.swing.JComponent

//noinspection ApiStatus
object WorksheetDiffSplitters {
  private val Log = Logger.getInstance(this.getClass)

  private val COLOR1 = JBColor.GRAY
  private val COLOR2 = JBColor.LIGHT_GRAY

  class SimpleWorksheetSplitter private[WorksheetDiffSplitters](
    editor1: Editor,
    editor2: Editor,
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

    private def init(): Unit = {
      setDividerWidth(30)
      setFirstComponent(editor1.getComponent)
      setSecondComponent(editor2.getComponent)
      setHonorComponentsMinimumSize(false)

      getDivider.addMouseListener(new MouseAdapter {
        override def mouseReleased(mouseEvent: MouseEvent): Unit = {
          val documentManager = PsiDocumentManager.getInstance(editor1.getProject)
          val file = documentManager.getCachedPsiFile(editor1.getDocument)
          file match {
            case file: ScalaFile =>
              WorksheetEditorPrinterFactory.saveOnlyRatio(file.getVirtualFile, getProportion)
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
   * @param end   (inclusive)
   */
  private case class Segment(start: Int, end: Int) {
    def intersects(other: Segment): Boolean = start <= other.end && other.start <= end
    def * (a: Int): Segment = Segment(start * a, end * a)
  }

  @RequiresEdt
  def addSplitter(
    editor: Editor,
    viewer: Editor,
    proportion: Float,
  ): SimpleWorksheetSplitter = {
    val editorComponent: JComponent = editor.getComponent
    val editorContentComponent: JComponent = editor.getContentComponent

    val editorComponentParent = editorComponent.getParent
    val textEditorComponent = editorComponentParent.asInstanceOf[TextEditorComponent]

    //NOTE: we need to get constraint before creating splitter, because latter will modify the UI structure
    val constraint =getGridBagConstraint(textEditorComponent)

    //NOTE: `createSimpleSplitter` deletes `editorComponent` from `editorComponentParent` children
    val splitter = new SimpleWorksheetSplitter(editor, viewer, Nil, proportion)

    preserveFocus(editorContentComponent) {
      textEditorComponent.remove(editorComponent) //NOTE: seems like no need to do this, after we create splitter this component is already deleted
      textEditorComponent.__add(splitter, constraint)
    }

    splitter
  }

  private def preserveFocus(component: Component)(body: => Unit): Unit = {
    val hadFocus = component.hasFocus

    body

    if (hadFocus) {
      component.requestFocusInWindow()
    }
  }

  private def getGridBagConstraint(textEditorComponent: TextEditorComponent): GridBagConstraints = {
    val components = textEditorComponent.getComponents
    val layout = textEditorComponent.getLayout.asInstanceOf[GridBagLayout]
    Log.assertTrue(components.size == 1, s"Expected 1 child component in text editor component but got ${components.size}")
    val firstComponent = components(0)
    layout.getConstraints(firstComponent)
  }

  @RequiresEdt
  def removeSplitter(
    editor: Editor
  ): Unit = {
    val editorComponent = editor.getComponent
    val splitter = editorComponent.getParent match {
      case s: SimpleWorksheetSplitter => s
      case _ =>
        return
    }
    val textEditorComponent = splitter.getParent.asInstanceOf[TextEditorComponent]

    val constraint = getGridBagConstraint(textEditorComponent)
    textEditorComponent.remove(splitter)
    textEditorComponent.__add(editorComponent, constraint)
  }
}
