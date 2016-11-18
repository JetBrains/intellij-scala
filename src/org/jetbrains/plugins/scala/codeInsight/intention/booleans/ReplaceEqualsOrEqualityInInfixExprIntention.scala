package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * @author Ksenia.Sautina
 * @since 4/23/12
 */

object ReplaceEqualsOrEqualityInInfixExprIntention {
  def familyName = "Replace equals or equality in infix expression"
}

class ReplaceEqualsOrEqualityInInfixExprIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = ReplaceEqualsOrEqualityInInfixExprIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val oper = infixExpr.operation.nameId.getText

    if (oper != "equals" && oper != "==") return false

    val range: TextRange = infixExpr.operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    val replaceOper = Map("equals" -> "==", "==" -> "equals")
    setText("Replace '" + oper + "' with '" + replaceOper(oper) + "'")

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val start = infixExpr.getTextRange.getStartOffset

    val expr = new StringBuilder
    val replaceOper = Map("equals" -> "==", "==" -> "equals")
    expr.append(infixExpr.getBaseExpr.getText).append(" ").append(replaceOper(infixExpr.operation.nameId.getText)).
            append(" ").append(infixExpr.getArgExpr.getText)

    val newInfixExpr = createExpressionFromText(expr.toString())(element.getManager)

    val size = newInfixExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
            newInfixExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replace(newInfixExpr)
      editor.getCaretModel.moveToOffset(start + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }

  }
}
