package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */


object MergeElseIfIntention {
  def familyName = "Merge Else If"
}

class MergeElseIfIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = MergeElseIfIntention.familyName

  override def getText: String = "Merge 'else if'"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null) return false

    val offset = editor.getCaretModel.getOffset
    val thenBranch = ifStmt.thenExpression.orNull
    val elseBranch = ifStmt.elseExpression.orNull
    if (thenBranch == null || elseBranch == null) return false

    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset))
      return false

    val blockExpr = ifStmt.elseExpression.orNull
    if (blockExpr != null && blockExpr.isInstanceOf[ScBlockExpr]) {
      val exprs = blockExpr.asInstanceOf[ScBlockExpr].exprs
      if (exprs.size == 1 && exprs.head.isInstanceOf[ScIf]) {
        return true
      }
    }

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val start = ifStmt.getTextRange.getStartOffset
    val startIndex = ifStmt.thenExpression.get.getTextRange.getEndOffset - ifStmt.getTextRange.getStartOffset
    val endIndex = ifStmt.elseExpression.get.getTextRange.getStartOffset - ifStmt.getTextRange.getStartOffset
    val elseIndex = ifStmt.getText.substring(startIndex, endIndex).indexOf("else") - 1
    val diff = editor.getCaretModel.getOffset - ifStmt.thenExpression.get.getTextRange.getEndOffset - elseIndex
    val newlineBeforeElse = ifStmt.children.find(_.getNode.getElementType == ScalaTokenTypes.kELSE).
      exists(_.getPrevSibling.getText.contains("\n"))
    val expr = new StringBuilder
    expr.append("if (").append(ifStmt.condition.get.getText).append(") ").
    append(ifStmt.thenExpression.get.getText).append(if (newlineBeforeElse) "\n" else " ").append("else ").
    append(ifStmt.elseExpression.get.getText.trim.drop(1).dropRight(1))

    val newIfStmt = createExpressionFromText(expr.toString())(element.getManager)
    val size = newIfStmt.asInstanceOf[ScIf].thenExpression.get.getTextRange.getEndOffset -
    newIfStmt.asInstanceOf[ScIf].getTextRange.getStartOffset

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}