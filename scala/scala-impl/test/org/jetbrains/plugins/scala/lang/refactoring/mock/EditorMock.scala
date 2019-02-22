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

    override def addListener(listener: Listener, disposable: Disposable): Unit = {}

    override def getInlineElementsInRange(i: Int, i1: Int): util.List[Inlay[_]] = util.Arrays.asList()

    override def getElementAt(point: Point): Inlay[_] = null

    override def getInlineElementAt(visualPosition: VisualPosition): Inlay[_] = null

    override def getBlockElementsInRange(i: Int, i1: Int): util.List[Inlay[_]] = new util.ArrayList[Inlay[_]](0)

    override def getBlockElementsForVisualLine(i: Int, b: Boolean): util.List[Inlay[_]] = new util.ArrayList[Inlay[_]](0)

    override def addInlineElement[T <: EditorCustomElementRenderer](offset: Int, relatesToPrecedingText: Boolean, renderer: T): Inlay[T] = null

    override def addBlockElement[T <: EditorCustomElementRenderer](offset: Int, relatesToPrecedingText: Boolean, showAbove: Boolean, priority: Int, renderer: T): Inlay[T] = null

    override def setConsiderCaretPositionOnDocumentUpdates(enabled: Boolean): Unit = {}

    def addAfterLineEndElement[T <: EditorCustomElementRenderer](offset: Int, relatesToPrecedingText: Boolean, renderer: T): Inlay[T] = null

    def getAfterLineEndElementsInRange(startOffset: Int, endOffset: Int): util.List[Inlay[_ <: EditorCustomElementRenderer]] = null

    def getAfterLineEndElementsForLogicalLine(logicalLine: Int): util.List[Inlay[_ <: EditorCustomElementRenderer]] = null
  }

  override def offsetToLogicalPosition(offset: Int): LogicalPosition = {
    val s = text.take(offset)
    new LogicalPosition(s.count(_ == '\n'),
      s.reverse.takeWhile(_ != '\n').length) // Workaround for SI-5971 (should be "s.view.reverse.")
  }

  override def logicalPositionToOffset(pos: LogicalPosition): Int =
    text.split('\n').view.map(_.length + 1).take(pos.line).sum + pos.column

  override def getDocument: Document = new DocumentImpl(text)

  override def getSelectionModel: SelectionModel = selection

  override def getCaretModel: CaretModel =
    new CaretModelMock(offset, offsetToLogicalPosition(offset))

  override def offsetToVisualPosition(i: Int, b: Boolean, b1: Boolean): VisualPosition = null

  override def xyToVisualPosition(p: Point2D): VisualPosition = null

  override def visualPositionToPoint2D(pos: VisualPosition): Point2D = null
}