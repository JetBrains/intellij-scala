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
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIf, ScInfixExpr, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

final class SplitIfIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element.parentOfType(classOf[ScIf], strict = false).exists {
      case ScIf(Some(ScInfixExpr(_, operation, _)), _, _) if caretIsInRange(operation)(editor) =>
        operation.refName == "&&"
      case _ => false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val ifStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val ScIf(Some(infix: ScInfixExpr), Some(ElementText(thenBranchText)), maybeElseBranch) = ifStmt
    val ScInfixExpr.withAssoc(base, _, argument) = infix

    def conditionText(e: ScExpression): String = (e match {
      case ScParenthesisedExpr(expression) => expression
      case expression => expression
    }).getText.trim

    val prefix =
      s"""if (${conditionText(base)})
         |if (${conditionText(argument)}) $thenBranchText""".stripMargin

    val suffix = maybeElseBranch match {
      case Some(ElementText(text)) =>
        val separator = if (prefix.trim.endsWith("}")) ' ' else '\n'
        s"""${separator}else $text
           |else $text""".stripMargin
      case _ => ""
    }

    import ifStmt.projectContext
    val start = ifStmt.getTextRange.getStartOffset
    val newIfStmt = createExpressionFromText(prefix + suffix).asInstanceOf[ScIf]
    val diff = newIfStmt.condition.get.getTextRange.getStartOffset - newIfStmt.getTextRange.getStartOffset

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.split.if")

  override def getText: String = ScalaCodeInsightBundle.message("split.into.2.if.s")
}
