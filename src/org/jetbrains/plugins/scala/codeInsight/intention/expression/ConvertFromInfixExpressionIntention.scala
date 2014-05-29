package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import lang.psi.api.expr._
import com.intellij.openapi.util.TextRange

/**
 * @author Ksenia.Sautina
 * @since 4/9/12
 */
object ConvertFromInfixExpressionIntention {
  val familyName = "Convert from infix expression"

  def createEquivMethodCall(infixExpr: ScInfixExpr): ScMethodCall = {
    val baseText = infixExpr.getBaseExpr.getText
    val opText = infixExpr.operation.getText
    val argText = infixExpr.getArgExpr match {
      case x: ScTuple =>  x.getText
      case x: ScParenthesisedExpr =>  x.getText
      case _ =>  s"(${infixExpr.getArgExpr.getText})"
    }
    val exprText = s"($baseText).$opText$argText"

    val exprA : ScExpression = ScalaPsiElementFactory.createExpressionFromText(baseText, infixExpr.getManager)

    val methodCallExpr = ScalaPsiElementFactory.createExpressionFromText(exprText.toString, infixExpr.getManager).asInstanceOf[ScMethodCall]
    methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get.replaceExpression(exprA, removeParenthesis = true)
    methodCallExpr
  }
}

class ConvertFromInfixExpressionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ConvertFromInfixExpressionIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr : ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false
    val range: TextRange = infixExpr.operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    range.getStartOffset <= offset && offset <= range.getEndOffset
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr : ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infixExpr.operation.nameId.getTextRange.getStartOffset
    val baseText = infixExpr.getBaseExpr.getText
    val opText = infixExpr.operation.getText
    val argText = infixExpr.getArgExpr match {
      case x: ScTuple =>  x.getText
      case x: ScParenthesisedExpr =>  x.getText
      case _ =>  s"(${infixExpr.getArgExpr.getText})"
    }
    val exprText = s"($baseText).$opText$argText"

    val exprA : ScExpression = ScalaPsiElementFactory.createExpressionFromText(baseText, element.getManager)

    val methodCallExpr : ScExpression = ScalaPsiElementFactory.createExpressionFromText(exprText.toString, element.getManager)
    methodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get.replaceExpression(exprA, removeParenthesis = true)
    val size = methodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getTextRange.getStartOffset -
       methodCallExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replaceExpression(methodCallExpr, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }

  }
}
