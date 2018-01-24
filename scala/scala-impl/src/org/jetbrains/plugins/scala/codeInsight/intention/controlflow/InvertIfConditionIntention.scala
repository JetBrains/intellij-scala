package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScIfStmt, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
  * @author Ksenia.Sautina
  * @since 6/6/12
  */
class InvertIfConditionIntention extends PsiElementBaseIntentionAction {

  import InvertIfConditionIntention._

  def getFamilyName: String = familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val thenBranch = ifStmt.thenBranch.orNull
    if (thenBranch == null) return false

    val condition = ifStmt.condition.orNull
    if (condition == null) return false

    val offset = editor.getCaretModel.getOffset
    if (!(ifStmt.getTextRange.getStartOffset <= offset && offset <= condition.getTextRange.getStartOffset - 1))
      return false

    val elseBranch = ifStmt.elseBranch.orNull
    if (elseBranch != null) return elseBranch.isInstanceOf[ScBlockExpr]

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    import IntentionUtils.negate

    val expr = new StringBuilder
    val newCond = ifStmt.condition.get match {
      case ScInfixExpr.withAssoc(base, operation, argument) =>
        val refName = operation.refName

        def negateBoolOperation(expression: ScExpression) = refName match {
          case "||" | "&&" => negate(expression)
          case _ => expression.getText
        }

        s"${negateBoolOperation(base)} ${Replacement(refName)} ${negateBoolOperation(argument)}"
      case condition => negate(condition)
    }

    val elseBranch = ifStmt.elseBranch.orNull
    val newThenBranch = if (elseBranch != null) elseBranch.asInstanceOf[ScBlockExpr].getText else "{\n\n}"
    expr.append("if (").append(newCond).append(")").append(newThenBranch).append(" else ")
    val res = ifStmt.thenBranch.get match {
      case e: ScBlockExpr => e.getText
      case _ => "{\n" + ifStmt.thenBranch.get.getText + "\n}"
    }
    expr.append(res)

    inWriteAction {
      ifStmt.replaceExpression(createExpressionFromText(expr.toString())(element.getManager), true)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}

object InvertIfConditionIntention {

  def familyName = "Invert If condition"

  private val Replacement = Map(
    "==" -> "!=",
    "!=" -> "==",
    ">" -> "<=",
    "<" -> ">=",
    ">=" -> "<",
    "<=" -> ">",
    "&&" -> "||",
    "||" -> "&&"
  )
}
