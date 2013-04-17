package org.jetbrains.plugins.scala
package codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScBlockExpr, ScWhileStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._

/**
 * Nikolay.Tropin
 * 4/17/13
 */

object ReplaceWhileWithDoWhileIntention {
  def familyName = "Replace while with do while"
}

class ReplaceWhileWithDoWhileIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ReplaceWhileWithDoWhileIntention.familyName

  override def getText: String = ReplaceWhileWithDoWhileIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val whileStmt: ScWhileStmt = PsiTreeUtil.getParentOfType(element, classOf[ScWhileStmt], false)
    if (whileStmt == null) false

    val condition = whileStmt.condition.getOrElse(null)
    if (condition == null) false

    val body = whileStmt.body.getOrElse(null)
    if (body == null) false

    val offset = editor.getCaretModel.getOffset
    if (offset < whileStmt.getTextRange.getStartOffset || offset > condition.getTextRange.getStartOffset - 1)
      false

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val whileStmt : ScWhileStmt = PsiTreeUtil.getParentOfType(element, classOf[ScWhileStmt], false)
    if (whileStmt == null || !whileStmt.isValid) return

    if (false)
    {print("")}
    else
    {print("")}

    val condition = whileStmt.condition.getOrElse(null)
    val condText = if (condition != null) condition.getText() else return

    val body = whileStmt.body.getOrElse(null)
    val bodyText = if (body == null) "{\n\n}" else body match {
      case e: ScBlockExpr => e.getText
      case _ => "{\n" + body.getText + "\n}"
    }

    val expr = new StringBuilder
    expr.append("if (").append(condText).append(") {\n")
    expr.append("do ").append(bodyText).append(" while (").append(condText).append(")\n")
    expr.append("}")

    val newStmt : ScExpression = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

    inWriteAction {
      whileStmt.replaceExpression(newStmt, true)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }

  }
}
