package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.expr._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.{IntentionAvailabilityChecker, IntentionUtils}

/**
 * @author Ksenia.Sautina
 * @since 4/9/12
 */

object ConvertToInfixExpressionIntention {
  val familyName = "Convert to infix expression"
}

class ConvertToInfixExpressionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ConvertToInfixExpressionIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!IntentionAvailabilityChecker.check(this, element)) return false
    val methodCallExpr : ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false
    if (!methodCallExpr.getInvokedExpr.isInstanceOf[ScReferenceExpression]) return false
    val range: TextRange = ((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false
    if (((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).isQualified) return true
    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val start = methodCallExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset -
            ((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).nameId.getTextRange.getStartOffset

    var putArgsFirst = false
    val argsBuilder = new StringBuilder
    val invokedExprBuilder = new StringBuilder

    val qual = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get
    val oper = ((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).nameId
    val invokedExprText = methodCallExpr.getInvokedExpr.getText
    val methodCallArgs = methodCallExpr.args

    if (invokedExprText.last == ':') {
      putArgsFirst = true
      invokedExprBuilder.append(oper.getText).append(" ").append(qual.getText)
    } else {
      invokedExprBuilder.append(qual.getText).append(" ").append(oper.getText)
    }

    argsBuilder.append(methodCallArgs.getText)

    IntentionUtils.analyzeMethodCallArgs(methodCallArgs, argsBuilder)

    var forA = qual.getText
    if (forA.startsWith("(") && forA.endsWith(")")) {
      forA = qual.getText.drop(1).dropRight(1)
    }

    var forB = argsBuilder.toString()
    if (forB.startsWith("(") && forB.endsWith(")")) {
      forB = argsBuilder.toString().drop(1).dropRight(1)
    }

    val exprA : ScExpression = ScalaPsiElementFactory.createExpressionFromText(forA, element.getManager)
    val exprB : ScExpression = ScalaPsiElementFactory.createExpressionFromText(forB, element.getManager)

    val expr = putArgsFirst match {
      case true => argsBuilder.append(" ").append(invokedExprBuilder)
      case false =>  invokedExprBuilder.append(" ").append(argsBuilder)
    }

    val infixExpr = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)
    infixExpr.asInstanceOf[ScInfixExpr].getBaseExpr.replaceExpression(exprA, true)
    infixExpr.asInstanceOf[ScInfixExpr].getArgExpr.replaceExpression(exprB, true)

    val size = infixExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
            infixExpr.getTextRange.getStartOffset

    inWriteAction {
      methodCallExpr.replaceExpression(infixExpr, true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

}
