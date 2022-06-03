package org.jetbrains.plugins.scala.annotator.hints

import com.intellij.openapi.editor.Editor

import java.awt.Point

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

  def apply(errorTooltip: ErrorTooltip, editor: Editor): TooltipUI = {
    errorTooltip match {
      case ErrorTooltip.JustText(message) =>
        HintUI(message)
      case ErrorTooltip.WithAction(message, action, element) =>
        if (element.isValid && action.isAvailable(element.getProject, editor, element.getContainingFile))
          PopupUI(message, action, element, editor)
        else
          HintUI(message)
    }
  }
}
