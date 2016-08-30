package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

object FlipComparisonInInfixExprIntention {
  def familyName = "Flip comparison in infix expression."
}

class FlipComparisonInInfixExprIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = FlipComparisonInInfixExprIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val oper = infixExpr.operation.nameId.getText

    if (oper != "equals" && oper != "==" && oper != "!=" && oper != "eq" && oper != "ne" &&
            oper != ">" && oper != "<" && oper != ">=" && oper != "<=" &&
            oper != "&&" && oper != "||")
      return false

    val range: TextRange = infixExpr.operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    val notChanged = mutable.HashSet[String]("==", "!=", "equals", "eq", "ne", "&&", "||")
    if (notChanged.contains(oper)) {
      setText("Flip '" + oper + "'" )
    }   else  {
      val replaceOper = Map(">" -> "<", "<" -> ">", ">=" -> "<=", "<=" -> ">=")
      setText("Flip '" + oper + "' to '" + replaceOper(oper) + "'")
    }

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infixExpr.operation.nameId.getTextRange.getStartOffset
    val expr = new StringBuilder
    val replaceOper = Map("equals" -> "equals","==" -> "==", "!=" -> "!=", "eq" -> "eq", "ne" -> "ne",
                          ">" -> "<", "<" -> ">", ">=" -> "<=", "<=" -> ">=", "&&" -> "&&", "||" -> "||")

    expr.append(infixExpr.getArgExpr.getText).append(" ").
            append(replaceOper(infixExpr.operation.nameId.getText)).append(" ").append(infixExpr.getBaseExpr.getText)

    val newInfixExpr = createExpressionFromText(expr.toString())(element.getManager)

    val size = newInfixExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
            newInfixExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replace(newInfixExpr)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

}
