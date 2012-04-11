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
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr.xml.ScXmlExpr
import com.intellij.openapi.util.TextRange

/**
 * @author Ksenia.Sautina
 * @since 4/9/12
 */

object ConvertToInfixMethodCallIntention {
  val familyName = "Convert to infix method call"
}

class ConvertToInfixMethodCallIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ConvertToInfixMethodCallIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
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

    val qual = methodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get
    val oper = ((methodCallExpr.getInvokedExpr).asInstanceOf[ScReferenceExpression]).nameId
    var invokedExprText = methodCallExpr.getInvokedExpr.getText
    val methodCallArgs = methodCallExpr.args

    if (invokedExprText.last == ':') {
      putArgsFirst = true
      invokedExprBuilder.append(oper.getText).append(" ").append(qual.getText)
    } else {
      invokedExprBuilder.append(qual.getText).append(" ").append(oper.getText)
    }

    argsBuilder.append(methodCallArgs.getText)

    if (methodCallArgs.exprs.length == 1) {
      methodCallArgs.exprs.head match {
        case _: ScLiteral => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScTuple => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScReferenceExpression => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScGenericCall => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScXmlExpr => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScMethodCall => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _ =>  argsBuilder
      }
    }

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
