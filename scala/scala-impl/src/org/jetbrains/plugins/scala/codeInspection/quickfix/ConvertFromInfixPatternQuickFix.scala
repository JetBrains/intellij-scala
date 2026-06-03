package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import com.intellij.openapi.command.undo.UndoUtil.markPsiFileForUndo
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.quickfix.ConvertFromInfixPatternQuickFix.message
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScInfixPattern, ScParenthesisedPattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class ConvertFromInfixPatternQuickFix(expr: ScInfixPattern) extends PsiUpdateModCommandAction[ScInfixPattern](expr) {
  override def getFamilyName: String = message

  override def invoke(context: ActionContext, infixPattern: ScInfixPattern, updater: ModPsiUpdater): Unit =
    ConvertFromInfixPatternQuickFix.applyFix(infixPattern)(context.project())
}

object ConvertFromInfixPatternQuickFix {
  val message: String = ScalaInspectionBundle.message("convert.from.infix.pattern")

  def applyFix(infixPattern: ScInfixPattern)(implicit project: Project): Unit = {
    val replacement = ScalaPsiElementFactory.createPatternFromText(computeReplacementText(infixPattern), infixPattern)

    val elementToReplace = infixPattern.getContext match {
      case x: ScParenthesisedPattern => x
      case _ => infixPattern
    }

    elementToReplace.replace(replacement)
    markPsiFileForUndo(replacement.getContainingFile)
  }

  private def computeReplacementText(infixPattern: ScInfixPattern): String = {
    val leftText = infixPattern.left.getText
    val operationText = infixPattern.operation.getText
    val argsText = infixPattern.rightOption match {
      case Some(right) => s"$leftText, ${right.getText}"
      case _ => leftText
    }

    s"$operationText($argsText)"
  }
}
