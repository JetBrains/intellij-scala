package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.openapi.command.undo.UndoUtil.markPsiFileForUndo
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.codeInspection.quickfix.ConvertFromInfixTypeQuickFix.message
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParenthesisedTypeElement}

final class ConvertFromInfixTypeQuickFix(expr: ScInfixTypeElement)
  extends AbstractFixOnPsiElement(message, expr)
    with DumbAware {
  override protected def doApplyFix(infixType: ScInfixTypeElement)(implicit project: Project): Unit =
    ConvertFromInfixTypeQuickFix.applyFix(infixType)
}

object ConvertFromInfixTypeQuickFix {
  val message: String = ScalaInspectionBundle.message("convert.from.infix.type")

  def applyFix(infixTypeElement: ScInfixTypeElement): Unit = {
    val replacement = infixTypeElement.computeDesugarizedType
      .getOrElse(return)

    val elementToReplace = infixTypeElement.getParent match {
      case x: ScParenthesisedTypeElement => x
      case _ => infixTypeElement
    }

    elementToReplace.replace(replacement)
    markPsiFileForUndo(replacement.getContainingFile)
  }
}
