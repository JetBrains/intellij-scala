package org.jetbrains.plugins.scala.annotator.hints

import java.awt.event.MouseEvent

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay

trait TooltipUI {
  final def show(inlay: Inlay[_], e: MouseEvent): this.type = {
    showImpl(inlay, e)
    this
  }

  protected def showImpl(inlay: Inlay[_], e: MouseEvent): Unit

  def isVisible: Boolean

  def hide(): Unit

  def addHideListener(action: () => Unit): Unit
}

object TooltipUI {

  def apply(errorTooltip: ErrorTooltip, editor: Editor): TooltipUI = {
    errorTooltip match {
      case ErrorTooltip.JustText(message) =>
        HintUI(message, editor)
      case ErrorTooltip.WithAction(message, tooltipAction) =>
        PopupUI(message, tooltipAction, editor)
    }
  }
}
