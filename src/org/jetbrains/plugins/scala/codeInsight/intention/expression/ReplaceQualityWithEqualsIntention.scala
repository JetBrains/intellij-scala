package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr.{ScReferenceExpression, ScMethodCall, ScInfixExpr}

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

object ReplaceQualityWithEqualsIntention {
  def familyName = "Replace '==' with 'equals'"
}

class ReplaceQualityWithEqualsIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ReplaceQualityWithEqualsIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr != null) {
      if (infixExpr.operation.nameId.getText != "==") return false

      val range: TextRange = infixExpr.operation.nameId.getTextRange
      val offset = editor.getCaretModel.getOffset
      if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

      return true
    } else {
      val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
      if (methodCallExpr == null) return false

      if (!methodCallExpr.getInvokedExpr.isInstanceOf[ScReferenceExpression]) return false
      if (methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getText != "==") return false

      val range: TextRange = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getTextRange
      val offset = editor.getCaretModel.getOffset
      if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false
      if (((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).isQualified) return true

      return false
    }
    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)

    if (infixExpr != null) {
      if (!infixExpr.isValid) return

      val start = infixExpr.getTextRange.getStartOffset

      val expr = new StringBuilder
      expr.append(infixExpr.getBaseExpr.getText).append(" equals ").append(infixExpr.getArgExpr.getText)

      val newInfixExpr = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

      val size = newInfixExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
              newInfixExpr.getTextRange.getStartOffset

      inWriteAction {
        infixExpr.replace(newInfixExpr)
        editor.getCaretModel.moveToOffset(start + size)
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      }
    } else {
      val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
      if (methodCallExpr == null || !methodCallExpr.isValid) return

      val start = methodCallExpr.getTextRange.getStartOffset

      val expr = new StringBuilder
      expr.append(methodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].
              qualifier.get.getText).append(".equals").append(methodCallExpr.args.getText)

      val newMethodCallExpr = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

      val size = newMethodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.
              getTextRange.getStartOffset - newMethodCallExpr.getTextRange.getStartOffset

      inWriteAction {
        methodCallExpr.replace(newMethodCallExpr)
        editor.getCaretModel.moveToOffset(start + size)
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      }


    }

  }

}
