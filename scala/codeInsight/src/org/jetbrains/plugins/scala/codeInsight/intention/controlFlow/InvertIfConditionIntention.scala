package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScIf, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

final class InvertIfConditionIntention extends PsiElementBaseIntentionAction {

  import InvertIfConditionIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null) return false

    val thenExpression = ifStmt.thenExpression.orNull
    if (thenExpression == null) return false

    val condition = ifStmt.condition.orNull
    if (condition == null) return false

    val elseExpression = ifStmt.elseExpression.orNull
    val offset = editor.getCaretModel.getOffset

    val caretIsOnIf = ifStmt.getTextRange.getStartOffset <= offset && offset < condition.getTextRange.getStartOffset
    val caretIsOnElse = isCaretOnElse(thenExpression, elseExpression, offset)

    caretIsOnIf || caretIsOnElse
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val condition = ifStmt.condition.orNull
    val thenExpression = ifStmt.thenExpression.orNull
    val elseExpression = ifStmt.elseExpression.orNull

    val newCondition: String = condition match {
      case ScInfixExpr.withAssoc(base, operation, argument) =>
        val refName = operation.refName

        def negateBoolOperation(expression: ScExpression) = refName match {
          case "||" | "&&" => negate(expression)
          case _ => expression.getText
        }

        s"${negateBoolOperation(base)} ${Replacement(refName)} ${negateBoolOperation(argument)}"
      case _ =>
        negate(condition)
    }

    val newThenExpression: String = elseExpression match {
      case null => "{\n\n}"
      case block: ScBlockExpr => block.getText
      case expr => "{\n" + expr.getText + "\n}"
    }

    val newElseExpression: String = thenExpression match {
      case block: ScBlockExpr => block.getText
      case expr => "{\n" + expr.getText + "\n}"
    }

    val newIfElseText = s"if ($newCondition) $newThenExpression else $newElseExpression"

    val caretModel = editor.getCaretModel
    val oldCaretWasOnElse = isCaretOnElse(thenExpression, elseExpression, caretModel.getOffset)

    inWriteAction {
      val newIfStmtDummy = createExpressionFromText(newIfElseText)(element.getManager)
      val newIfStmt = ifStmt.replaceExpression(newIfStmtDummy, removeParenthesis = true)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)

      if (oldCaretWasOnElse) {
        for {
          newIf <- newIfStmt.toOption.filterByType[ScIf]
          newThen <- newIf.thenExpression
        } caretModel.moveToOffset(newThen.getTextRange.getEndOffset)
      }
    }
  }

  private def isCaretOnElse(thenBranch: ScExpression, elseBranch: ScExpression, offset: Int) = {
    elseBranch != null &&
      thenBranch.getTextRange.getEndOffset <= offset && offset < elseBranch.getTextRange.getStartOffset
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.invert.if.condition")

  override def getText: String = getFamilyName
}

object InvertIfConditionIntention {
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
