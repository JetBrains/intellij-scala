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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScWhile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

final class ReplaceWhileWithDoWhileIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    for {
      whileStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScWhile], false))
      condition <- whileStmt.condition
      body <- whileStmt.expression
    } {
      val offset = editor.getCaretModel.getOffset
      if (offset >= whileStmt.getTextRange.getStartOffset && offset <= condition.getTextRange.getStartOffset - 1)
        return true
    }

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val whileStmt: ScWhile = PsiTreeUtil.getParentOfType(element, classOf[ScWhile])
    if (whileStmt == null || !whileStmt.isValid) return

    for {
      condition <- whileStmt.condition
      body <- whileStmt.expression
    } {
      val condText = condition.getText
      val bodyText = body.getText

      val newStmtText = s"if ($condText) {\n do $bodyText while ($condText)\n}"

      val newStmt = createExpressionFromText(newStmtText)(element.getManager)

      inWriteAction {
        whileStmt.replaceExpression(newStmt, removeParenthesis = true)
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      }
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.replace.while.with.do.while")

  override def getText: String = getFamilyName
}

