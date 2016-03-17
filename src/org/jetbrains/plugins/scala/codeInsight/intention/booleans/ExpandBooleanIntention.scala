package org.jetbrains.plugins.scala.codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScParenthesisedExpr, ScReturnStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * @author Ksenia.Sautina
 * @since 6/29/12
 */

object ExpandBooleanIntention {
  def familyName = "Expand Boolean"
}

class ExpandBooleanIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ExpandBooleanIntention.familyName

  override def getText: String = "Expand boolean use to 'if else'"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val returnStmt: ScReturnStmt = PsiTreeUtil.getParentOfType(element, classOf[ScReturnStmt], false)
    if (returnStmt == null) return false

    val range: TextRange = returnStmt.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    implicit val typeSystem = project.typeSystem
    val value = returnStmt.expr.orNull
    if (value == null) return false
    val valType = value.getType(TypingContext.empty).getOrElse(null)
    if (valType == null) return false
    if (valType.canonicalText == "Boolean") return true

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val returnStmt: ScReturnStmt = PsiTreeUtil.getParentOfType(element, classOf[ScReturnStmt], false)
    if (returnStmt == null || !returnStmt.isValid) return

    val start = returnStmt.getTextRange.getStartOffset
    val expr = new StringBuilder
    val value = returnStmt.expr.orNull
    if (value == null) return
    expr.append("if ")

    value match {
      case v: ScParenthesisedExpr => expr.append(v.getText)
      case _ => expr.append("(").append(value.getText).append(")")
    }

    expr.append("{ return true } else { return false }")

    val newReturnStmt : ScExpression = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

    inWriteAction {
      returnStmt.replaceExpression(newReturnStmt, true)
      editor.getCaretModel.moveToOffset(start)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}