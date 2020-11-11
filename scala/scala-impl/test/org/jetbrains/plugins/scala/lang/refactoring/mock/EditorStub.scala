package org.jetbrains.plugins.scala.lang.refactoring.mock

import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import java.awt.{Insets, Point}
import java.util
import javax.swing.JComponent
import javax.swing.border.Border

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.InlayModel.Listener
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.{EditorMouseEventArea, EditorMouseListener, EditorMouseMotionListener}
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * Pavel Fatin
 */

class EditorStub extends Editor {
  override def putUserData[T](key: Key[T], value: T): Unit = {}

  override def getUserData[T](key: Key[T]): T = null.asInstanceOf[T]

  override def getIndentsModel: IndentsModel = null

  override def getHeaderComponent: JComponent = null

  override def hasHeaderComponent: Boolean = false

  override def setHeaderComponent(header: JComponent): Unit = {}

  override def getMouseEventArea(e: MouseEvent): EditorMouseEventArea = null

  override def getGutter: EditorGutter = null

  override def isOneLineMode: Boolean = false

  override def isColumnMode: Boolean = false

  override def isInsertMode: Boolean = false

  override def getProject: Project = null

  override def isDisposed: Boolean = false

  override def removeEditorMouseMotionListener(listener: EditorMouseMotionListener): Unit = {}

  override def addEditorMouseMotionListener(listener: EditorMouseMotionListener): Unit = {}

  override def removeEditorMouseListener(listener: EditorMouseListener): Unit = {}

  override def addEditorMouseListener(listener: EditorMouseListener): Unit = {}

  override def xyToVisualPosition(p: Point): VisualPosition = null

  override def xyToLogicalPosition(p: Point): LogicalPosition = null

  override def offsetToVisualPosition(offset: Int): VisualPosition = null

  override def offsetToLogicalPosition(offset: Int): LogicalPosition = null

  override def visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition = null

  override def visualPositionToXY(visible: VisualPosition): Point = null

  override def logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition = null

  override def logicalPositionToOffset(pos: LogicalPosition): Int = 0

  override def logicalPositionToXY(pos: LogicalPosition): Point = null

  override def getLineHeight: Int = 0

  override def getColorsScheme: EditorColorsScheme = null

  override def getSettings: EditorSettings = null

  override def getSoftWrapModel: SoftWrapModel = null

  override def getCaretModel: CaretModel = null

  override def getScrollingModel: ScrollingModel = null

  override def getFoldingModel: FoldingModel = null

  override def getMarkupModel: MarkupModel = null

  override def getSelectionModel: SelectionModel = null

  override def setBorder(border: Border): Unit = {}

  override def getContentComponent: JComponent = null

  override def getComponent: JComponent = null

  override def isViewer: Boolean = false

  override def getDocument: Document = null

  override def getInsets: Insets = null

  override def offsetToVisualPosition(i: Int, b: Boolean, b1: Boolean): VisualPosition = null

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

    override def addAfterLineEndElement[T <: EditorCustomElementRenderer](offset: Int, relatesToPrecedingText: Boolean, renderer: T): Inlay[T] = null

    override def getAfterLineEndElementsInRange(startOffset: Int, endOffset: Int): util.List[Inlay[_ <: EditorCustomElementRenderer]] = null

    override def getAfterLineEndElementsForLogicalLine(logicalLine: Int): util.List[Inlay[_ <: EditorCustomElementRenderer]] = null

    override def execute(batchMode: Boolean, operation: Runnable): Unit = {}

    override def isInBatchMode: Boolean = false

    override def addInlineElement[T <: EditorCustomElementRenderer](offset: Int, relatesToPrecedingText: Boolean, priority: Int, renderer: T): Inlay[T] = null
  }

  override def xyToVisualPosition(p: Point2D): VisualPosition = null

  override def visualPositionToPoint2D(pos: VisualPosition): Point2D = null

  override def getEditorKind: EditorKind = EditorKind.MAIN_EDITOR
}