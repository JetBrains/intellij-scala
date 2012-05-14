package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr.{ScReferenceExpression, ScMethodCall, ScInfixExpr}
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import com.intellij.psi.{PsiDocumentManager, PsiElement}

/**
 * @author Ksenia.Sautina
 * @since 4/23/12
 */

object ReplaceEqualsOrQualityInMethodCallExprIntention {
  def familyName = "Replace equals or quality in method call expression"
}

class ReplaceEqualsOrQualityInMethodCallExprIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ReplaceEqualsOrQualityInMethodCallExprIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false

    if (!methodCallExpr.getInvokedExpr.isInstanceOf[ScReferenceExpression]) return false

    val oper = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getText
    if (oper != "equals" && oper != "==") return false

    val range: TextRange = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    val replaceOper = Map("equals" -> "==", "==" -> "equals")
    setText("Replace '" + oper + "' with '" + replaceOper(oper) + "'")

    if (((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).isQualified) return true

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val start = methodCallExpr.getTextRange.getStartOffset

    val expr = new StringBuilder
    val replaceOper = Map("equals" -> "==", "==" -> "equals")
    val oper = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getText

    expr.append(methodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].
            qualifier.get.getText).append(".").append(replaceOper(oper)).append(methodCallExpr.args.getText)

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
