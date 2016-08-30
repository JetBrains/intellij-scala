package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.parentheses.UnnecessaryParenthesesUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * Nikolay.Tropin
 * 4/29/13
 */
object RemoveUnnecessaryParenthesesIntention {
  def familyName = "Remove unnecessary parentheses"
}

class RemoveUnnecessaryParenthesesIntention extends PsiElementBaseIntentionAction{
  def getFamilyName: String = RemoveUnnecessaryParenthesesIntention.familyName

  override def getText = "Remove unnecessary parentheses"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScParenthesisedExpr], false)).exists {
      UnnecessaryParenthesesUtil.canBeStripped(_, ignoreClarifying = false)
    }
  }

  def invoke(project: Project, editor: Editor, element: PsiElement) {
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScParenthesisedExpr])).map {
      case expr if UnnecessaryParenthesesUtil.canBeStripped(expr, ignoreClarifying = false) =>
        val stripped: String = UnnecessaryParenthesesUtil.getTextOfStripped(expr, ignoreClarifying = false)
        inWriteAction {
          expr.replaceExpression(createExpressionFromText(stripped)(expr.getManager), removeParenthesis = true)
        }
      case _ =>
    }
  }
}
