package org.jetbrains.plugins.scala.lang.refactoring.mock

import java.awt.event.MouseEvent
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
  def putUserData[T](key: Key[T], value: T): Unit = {}

  def getUserData[T](key: Key[T]): T = null.asInstanceOf[T]

  def getIndentsModel: IndentsModel = null

  def getHeaderComponent: JComponent = null

  def hasHeaderComponent: Boolean = false

  def setHeaderComponent(header: JComponent): Unit = {}

  def getMouseEventArea(e: MouseEvent): EditorMouseEventArea = null

  def getGutter: EditorGutter = null

  def isOneLineMode: Boolean = false

  def isColumnMode: Boolean = false

  def isInsertMode: Boolean = false

  def getProject: Project = null

  def isDisposed: Boolean = false

  def removeEditorMouseMotionListener(listener: EditorMouseMotionListener): Unit = {}

  def addEditorMouseMotionListener(listener: EditorMouseMotionListener): Unit = {}

  def removeEditorMouseListener(listener: EditorMouseListener): Unit = {}

  def addEditorMouseListener(listener: EditorMouseListener): Unit = {}

  def xyToVisualPosition(p: Point): VisualPosition = null

  def xyToLogicalPosition(p: Point): LogicalPosition = null

  def offsetToVisualPosition(offset: Int): VisualPosition = null

  def offsetToLogicalPosition(offset: Int): LogicalPosition = null

  def visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition = null

  def visualPositionToXY(visible: VisualPosition): Point = null

  def logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition = null

  def logicalPositionToOffset(pos: LogicalPosition): Int = 0

  def logicalPositionToXY(pos: LogicalPosition): Point = null

  def getLineHeight: Int = 0

  def getColorsScheme: EditorColorsScheme = null

  def getSettings: EditorSettings = null

  def getSoftWrapModel: SoftWrapModel = null

  def getCaretModel: CaretModel = null

  def getScrollingModel: ScrollingModel = null

  def getFoldingModel: FoldingModel = null

  def getMarkupModel: MarkupModel = null

  def getSelectionModel: SelectionModel = null

  def setBorder(border: Border): Unit = {}

  def getContentComponent: JComponent = null

  def getComponent: JComponent = null

  def isViewer: Boolean = false

  def getDocument: Document = null

  def getInsets: Insets = null

  override def offsetToVisualPosition(i: Int, b: Boolean, b1: Boolean): VisualPosition = null

  override def getInlayModel: InlayModel = new InlayModel {
    override def hasInlineElementAt(i: Int): Boolean = false

    override def hasInlineElementAt(visualPosition: VisualPosition): Boolean = false

    override def addInlineElement(i: Int, editorCustomElementRenderer: EditorCustomElementRenderer): Inlay = null

    override def addListener(listener: Listener, disposable: Disposable): Unit = {}

    override def getInlineElementsInRange(i: Int, i1: Int): util.List[Inlay] = util.Arrays.asList()

    override def getElementAt(point: Point): Inlay = null
  }
}