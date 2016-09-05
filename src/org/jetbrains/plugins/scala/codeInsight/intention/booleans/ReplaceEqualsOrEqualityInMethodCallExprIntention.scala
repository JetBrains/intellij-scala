package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInfixExprImpl

/**
  * @author Ksenia.Sautina
  * @since 4/23/12
  */

object ReplaceEqualsOrEqualityInMethodCallExprIntention {

  def familyName = "Replace equals or equality in method call expression"
  val replaceOper = Map("equals" -> "==", "==" -> "equals")
}

class ReplaceEqualsOrEqualityInMethodCallExprIntention extends PsiElementBaseIntentionAction {

  import ReplaceEqualsOrEqualityInMethodCallExprIntention._

  def getFamilyName: String = ReplaceEqualsOrEqualityInMethodCallExprIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false

    if (!methodCallExpr.getInvokedExpr.isInstanceOf[ScReferenceExpression]) return false

    val invokedExpression: ScReferenceExpression = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression]

    val oper = invokedExpression.nameId.getText
    if (!replaceOper.contains(oper)) return false

    val range: TextRange = invokedExpression.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (range.getStartOffset > offset || range.getEndOffset < offset) return false

    setText(s"Replace '$oper' with '${replaceOper(oper)}'")

    invokedExpression.isQualified
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {

    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val start = methodCallExpr.getTextRange.getStartOffset

    val scReferenceExpression: ScReferenceExpression = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression]
    val oper = scReferenceExpression.nameId.getText
    val desiredOper: String = replaceOper(oper)

    val convertedExpr: String = convertExpression(methodCallExpr, scReferenceExpression, desiredOper)

    val newMethodCallExpr = ScalaPsiElementFactory.createExpressionFromText(convertedExpr, element.getManager)

    val size = {
      if (desiredOper == "==") {
        newMethodCallExpr.asInstanceOf[ScInfixExprImpl].operation.nameId.getTextRange.getStartOffset - newMethodCallExpr.getTextRange.getStartOffset
      } else {
        newMethodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.
          getTextRange.getStartOffset - newMethodCallExpr.getTextRange.getStartOffset
      }
    }

    inWriteAction {
      methodCallExpr.replace(newMethodCallExpr)
      editor.getCaretModel.moveToOffset(start + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  def convertExpression(methodCallExpr: ScMethodCall, scReferenceExpression: ScReferenceExpression, desiredOper: String): String = {

    val methodCallArgs: ScArgumentExprList = methodCallExpr.args
    val methodCallArgsText: String = methodCallArgs.getText

    if (desiredOper == "==") {

      val processArgs: String = {
        //accounts for tuples
        if (methodCallArgs.getChildren.length == 1)
          methodCallArgsText.drop(1).dropRight(1)
        else
          methodCallArgsText
      }

      s"${scReferenceExpression.qualifier.get.getText} $desiredOper $processArgs"
    } else {

      s"${scReferenceExpression.qualifier.get.getText}.$desiredOper$methodCallArgsText"
    }
  }
}
