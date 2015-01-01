package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIfStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

object SplitElseIfIntention {
  def familyName = "Split Else If"
}

class SplitElseIfIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = SplitElseIfIntention.familyName

  override def getText: String = "Split 'else if'"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val offset = editor.getCaretModel.getOffset
    val thenBranch = ifStmt.thenBranch.orNull
    val elseBranch = ifStmt.elseBranch.orNull
    if (thenBranch == null || elseBranch == null) return false

    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset))
      return false

    val elseIfExpr = ifStmt.elseBranch.orNull
    if (elseIfExpr != null && elseIfExpr.isInstanceOf[ScIfStmt]) {
      return true
    }

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val start = ifStmt.getTextRange.getStartOffset
    val startIndex = ifStmt.thenBranch.get.getTextRange.getEndOffset - ifStmt.getTextRange.getStartOffset
    val endIndex = ifStmt.elseBranch.get.getTextRange.getStartOffset - ifStmt.getTextRange.getStartOffset
    val elseIndex = ifStmt.getText.substring(startIndex, endIndex).indexOf("else") - 1
    val diff = editor.getCaretModel.getOffset - ifStmt.thenBranch.get.getTextRange.getEndOffset - elseIndex

    val expr = new StringBuilder
    expr.append("if (").append(ifStmt.condition.get.getText).append(") ").
      append(ifStmt.thenBranch.get.getText).append(" else {\n").
      append(ifStmt.elseBranch.get.getText).append("\n}")

    val newIfStmt: ScExpression = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)
    val size = newIfStmt.asInstanceOf[ScIfStmt].thenBranch.get.getTextRange.getEndOffset -
      newIfStmt.asInstanceOf[ScIfStmt].getTextRange.getStartOffset

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}
