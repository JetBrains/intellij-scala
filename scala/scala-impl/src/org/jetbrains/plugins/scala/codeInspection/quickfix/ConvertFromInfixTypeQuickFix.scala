package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import com.intellij.openapi.command.undo.UndoUtil.markPsiFileForUndo
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.quickfix.ConvertFromInfixTypeQuickFix.message
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParenthesisedTypeElement}

final class ConvertFromInfixTypeQuickFix(expr: ScInfixTypeElement)
  extends PsiUpdateModCommandAction[ScInfixTypeElement](expr)
    with DumbAware {
  override def getFamilyName: String = message

  override def invoke(context: ActionContext, infixType: ScInfixTypeElement, updater: ModPsiUpdater): Unit =
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
