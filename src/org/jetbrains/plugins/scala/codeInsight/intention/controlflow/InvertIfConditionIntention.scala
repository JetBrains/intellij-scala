package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScExpression, ScBlockExpr, ScIfStmt}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.IntentionUtils
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

object InvertIfConditionIntention {
  def familyName = "Invert If condition"
}

class InvertIfConditionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = InvertIfConditionIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val thenBranch =  ifStmt.thenBranch.getOrElse(null)
    if (thenBranch == null) return false

    val condition = ifStmt.condition.getOrElse(null)
    if (condition == null) return false

    val offset = editor.getCaretModel.getOffset
    if (!(ifStmt.getTextRange.getStartOffset <= offset && offset <= condition.getTextRange.getStartOffset - 1))
      return false

    val elseBranch =  ifStmt.elseBranch.getOrElse(null)
    if (elseBranch != null) return elseBranch.isInstanceOf[ScBlockExpr]

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt : ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val expr = new StringBuilder
    val newCond = ifStmt.condition.get match {
      case infixExpr: ScInfixExpr =>
        val oper = infixExpr.operation.nameId.getText
        val first = if (oper == "||" || oper == "&&") {
          IntentionUtils.negate(infixExpr.getBaseExpr)
        } else {
          infixExpr.getBaseExpr.getText
        }
        val second = if (oper == "||" || oper == "&&") {
          IntentionUtils.negate(infixExpr.getArgExpr)
        } else {
          infixExpr.getArgExpr.getText
        }
        val replaceOper = Map("==" -> "!=", "!=" -> "==", ">" -> "<=", "<" -> ">=", ">=" -> "<", "<=" -> ">",
          "&&" -> "||", "||" -> "&&")
        val buf = new StringBuilder
        buf.append(first).append(" ").append(replaceOper(oper)).append(" ").append(second)
        buf.toString()
      case _ => IntentionUtils.negate(ifStmt.condition.get)
    }

    val elseBranch =  ifStmt.elseBranch.getOrElse(null)
    val newThenBranch = if (elseBranch != null) elseBranch.asInstanceOf[ScBlockExpr].getText else "{\n\n}"
    expr.append("if (").append(newCond).append(")").append(newThenBranch).append(" else ")
    val res =  ifStmt.thenBranch.get match {
      case e: ScBlockExpr => e.getText
      case _ => "{\n" + ifStmt.thenBranch.get.getText + "\n}"
    }
    expr.append(res)
    val newStmt : ScExpression = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

    inWriteAction {
      ifStmt.replaceExpression(newStmt, true)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}