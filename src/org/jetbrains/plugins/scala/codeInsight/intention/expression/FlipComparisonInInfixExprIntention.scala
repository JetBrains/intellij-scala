package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr._
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import com.intellij.psi.{PsiDocumentManager, PsiElement}

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

object FlipComparisonInInfixExprIntention {
  def familyName = "Swap the operands of a comparison expression."
}

class FlipComparisonInInfixExprIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = FlipComparisonInInfixExprIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val oper = infixExpr.operation.nameId.getText
    
    if (oper != "equals" && oper != "==" && oper != "!=" && oper != "eq" && oper != "ne" &&
        oper != ">" && oper != "<" && oper != ">=" && oper != "<=")
      return false

    val range: TextRange = infixExpr.operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false
    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr : ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infixExpr.operation.nameId.getTextRange.getStartOffset

    val expr = new StringBuilder

    val oper = infixExpr.operation.nameId.getText match {
      case "equals" => "equals"
      case "==" => "=="
      case "!=" => "!="
      case "eq" => "eq"
      case "ne" => "ne"
      case ">" => "<"
      case "<" => ">"
      case ">=" => "<="
      case "<=" => ">="
    }

    expr.append(infixExpr.getArgExpr.getText).append(" ").append(oper).
         append(" ").append(infixExpr.getBaseExpr.getText)

    val newInfixExpr = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

    val size = newInfixExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
            newInfixExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replace(newInfixExpr)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

}
