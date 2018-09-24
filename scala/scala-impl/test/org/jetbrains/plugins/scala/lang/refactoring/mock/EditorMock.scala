package org.jetbrains.plugins.scala.lang.refactoring.mock

import java.awt.Point
import java.awt.geom.Point2D
import java.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.InlayModel.Listener
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.impl.DocumentImpl

/**
 * Pavel Fatin
 */

class EditorMock(text: String, offset: Int) extends EditorStub {
  private val selection = new SelectionModelStub()

  override def getInlayModel: InlayModel = new InlayModel {
    override def hasInlineElementAt(i: Int): Boolean = false

    override def hasInlineElementAt(visualPosition: VisualPosition): Boolean = false

    override def addInlineElement(i: Int, editorCustomElementRenderer: EditorCustomElementRenderer): Inlay = null

    override def addInlineElement(offset: Int, relatesToPrecedingText: Boolean, renderer: EditorCustomElementRenderer): Inlay = null

    override def addListener(listener: Listener, disposable: Disposable): Unit = {}

    override def getInlineElementsInRange(i: Int, i1: Int): util.List[Inlay] = util.Arrays.asList()

    override def getElementAt(point: Point): Inlay = null

    override def getInlineElementAt(visualPosition: VisualPosition): Inlay = null

    override def addBlockElement(i: Int, b: Boolean, b1: Boolean, i1: Int, editorCustomElementRenderer: EditorCustomElementRenderer): Inlay = null

    override def getBlockElementsInRange(i: Int, i1: Int): util.List[Inlay] = new util.ArrayList[Inlay](0)

    override def getBlockElementsForVisualLine(i: Int, b: Boolean): util.List[Inlay] = new util.ArrayList[Inlay](0)
  }

  override def offsetToLogicalPosition(offset: Int) = {
    val s = text.take(offset)
    new LogicalPosition(s.count(_ == '\n'),
      s.reverse.takeWhile(_ != '\n').length) // Workaround for SI-5971 (should be "s.view.reverse.")
  }

  override def logicalPositionToOffset(pos: LogicalPosition) =
    text.split('\n').view.map(_.length + 1).take(pos.line).sum + pos.column

  override def getDocument: Document = new DocumentImpl(text)

  override def getSelectionModel: SelectionModel = selection

  override def getCaretModel: CaretModel =
    new CaretModelMock(offset, offsetToLogicalPosition(offset))

  override def offsetToVisualPosition(i: Int, b: Boolean, b1: Boolean): VisualPosition = null

  override def xyToVisualPosition(p: Point2D): VisualPosition = null

  override def visualPositionToPoint2D(pos: VisualPosition): Point2D = null
}