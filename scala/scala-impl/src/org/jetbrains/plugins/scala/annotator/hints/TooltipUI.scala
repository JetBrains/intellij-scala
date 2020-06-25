package org.jetbrains.plugins.scala.annotator.hints

import java.awt.Point

import com.intellij.openapi.editor.Editor

trait TooltipUI {
  val message: String

  final def show(editor: Editor, mousePoint: Point, inlayOffset: Int): this.type = {
    showImpl(editor, mousePoint, inlayOffset)
    this
  }

  protected def showImpl(editor: Editor, mousePoint: Point, inlayOffset: Int): Unit

  def isDisposed: Boolean

  def cancel(): Unit

  def addHideListener(action: () => Unit): Unit
}

object TooltipUI {

  private object NoOpUI extends TooltipUI {
    override val message: String = ""
    override protected def showImpl(editor: Editor, mousePoint: Point, inlayOffset: Int): Unit = ()
    override def isDisposed: Boolean = true
    override def cancel(): Unit = ()
    override def addHideListener(action: () => Unit): Unit = ()
  }

  def apply(errorTooltip: ErrorTooltip, editor: Editor): TooltipUI = {
    errorTooltip match {
      case ErrorTooltip.JustText(message) =>
        HintUI(message, editor)
      case ErrorTooltip.WithAction(message, action, element) =>
        if (element.isValid && action.isAvailable(element.getProject, editor, element.getContainingFile))
          PopupUI(message, action, element, editor)
        else
          NoOpUI
    }
  }
}
