package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Ksenia.Sautina
 * @since 4/23/12
 */

object ReplaceEqualsOrEqualityInMethodCallExprIntention {
  def familyName = "Replace equals or equality in method call expression"
}

class ReplaceEqualsOrEqualityInMethodCallExprIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ReplaceEqualsOrEqualityInMethodCallExprIntention.familyName

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

    expr.append(methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].
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
