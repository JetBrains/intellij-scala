package org.jetbrains.plugins.scala.annotator.hints

import java.awt.event.InputEvent

import com.intellij.codeInsight.hint.TooltipGroup
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.TooltipAction
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import scala.language.implicitConversions

sealed trait ErrorTooltip {
  def message: String
}

object ErrorTooltip {
  val tooltipGroup = new TooltipGroup("Scala inlay error tooltip", 0)

  implicit def fromString(message: String): ErrorTooltip = ErrorTooltip(message)

  def apply(message: String): ErrorTooltip = JustText(message)

  def apply(message: String, action: IntentionAction, element: PsiElement): ErrorTooltip = {
    WithAction(message, new TooltipAction {
      override def getText: String = action.getText

      override def execute(editor: Editor, event: InputEvent): Unit = {
        if (element.isValid) {
          action.invoke(element.getProject, editor, element.getContainingFile)
        }
      }

      override def showAllActions(editor: Editor): Unit = {
        if (element.isValid) {
          editor.getCaretModel.moveToOffset(element.endOffset)
          new ShowIntentionActionsHandler().invoke(element.getProject, editor, element.getContainingFile, true)
        }
      }
    })
  }

  case class JustText(override val message: String) extends ErrorTooltip

  case class WithAction(override val message: String, tooltipAction: TooltipAction) extends ErrorTooltip
}
