package org.jetbrains.plugins.scala.annotator.hints

import java.awt.Point

import com.intellij.openapi.editor.Editor

trait TooltipUI {
  val message: String

  final def show(editor: Editor, screenLocation: Point): this.type = {
    showImpl(editor, screenLocation)
    this
  }

  protected def showImpl(editor: Editor, screenLocation: Point): Unit

  def isDisposed: Boolean

  def cancel(): Unit

  def addHideListener(action: () => Unit): Unit
}

object TooltipUI {

  def apply(errorTooltip: ErrorTooltip, editor: Editor): TooltipUI = {
    errorTooltip match {
      case ErrorTooltip.JustText(message) =>
        HintUI(message, editor)
      case ErrorTooltip.WithAction(message, action, element) =>
        PopupUI(message, action, element, editor)
    }
  }
}
