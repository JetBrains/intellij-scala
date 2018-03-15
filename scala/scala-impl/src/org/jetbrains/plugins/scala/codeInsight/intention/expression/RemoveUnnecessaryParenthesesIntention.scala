package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.base.ScGenericParenthesisedNode.AnyParenthesisedNode

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

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean
  = Option(PsiTreeUtil.getParentOfType(element, classOf[AnyParenthesisedNode], false))
    .exists(_.isParenthesisRedundant())

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    Option(PsiTreeUtil.getParentOfType(element, classOf[AnyParenthesisedNode])) map {
      case expr if expr.isParenthesisRedundant() =>
        inWriteAction {
          expr.stripParentheses()
        }
      case _ =>
    }
  }
}
