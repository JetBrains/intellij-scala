package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIfStmt, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * @author Ksenia.Sautina
 * @since 6/8/12
 */

object SplitIfIntention {
  def familyName = "Split If"
}

class SplitIfIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = SplitIfIntention.familyName

  override def getText: String = "Split into 2 'if's"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val cond = ifStmt.condition.orNull
    if (cond == null || !cond.isInstanceOf[ScInfixExpr]) return false

    val range: TextRange = cond.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    if (cond.asInstanceOf[ScInfixExpr].operation.nameId.getText == "&&") return true

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val start = ifStmt.getTextRange.getStartOffset
    val expr = new StringBuilder
    val cond: ScInfixExpr = ifStmt.condition.get.asInstanceOf[ScInfixExpr]

    val firstCond =
      if (cond.getBaseExpr.getText.trim.startsWith("(") && cond.getBaseExpr.getText.trim.endsWith(")"))
        cond.getBaseExpr.getText.trim
      else "(" + cond.getBaseExpr.getText.trim + ")"
    val secondCond =
      if (cond.getArgExpr.getText.trim.startsWith("(") && cond.getArgExpr.getText.trim.endsWith(")"))
        cond.getArgExpr.getText.trim
      else "(" + cond.getArgExpr.getText.trim + ")"

    expr.append("if ").append(firstCond).append("\n").append("if ").append(secondCond).append(" ").
      append(ifStmt.thenBranch.get.getText)

    val elseBranch = ifStmt.elseBranch.orNull
    if (elseBranch != null) {
      if (expr.toString().trim.endsWith("}")) expr.append(" else ")
      else expr.append("\nelse ")
      expr.append(elseBranch.getText).append("\nelse ").append(elseBranch.getText)
    }

    val newIfStmt: ScExpression = createExpressionFromText(expr.toString())(element.getManager)
    val diff = newIfStmt.asInstanceOf[ScIfStmt].condition.get.getTextRange.getStartOffset -
      newIfStmt.asInstanceOf[ScIfStmt].getTextRange.getStartOffset

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}



