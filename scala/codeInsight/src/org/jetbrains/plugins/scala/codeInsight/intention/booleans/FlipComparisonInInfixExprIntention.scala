package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
  * @author Ksenia.Sautina
  * @since 4/20/12
  */
final class FlipComparisonInInfixExprIntention extends PsiElementBaseIntentionAction {

  import FlipComparisonInInfixExprIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName

    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        val suffix = if (replacement != refName) s" to '$replacement'" else ""
        setText(s"Flip '$refName' $suffix")
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val ScInfixExpr.withAssoc(ElementText(baseText), operation, ElementText(argumentText)) = infixExpr

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - operation.nameId.getTextRange.getStartOffset

    import infixExpr.projectContext
    val newInfixExpr = createExpressionFromText(s"$argumentText ${Replacement(operation.refName)} $baseText")

    val size = newInfixExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
      newInfixExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replace(newInfixExpr)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = FamilyName
}

object FlipComparisonInInfixExprIntention {

  private[booleans] val FamilyName = "Flip comparison in infix expression."

  private val Replacement = Map(
    "equals" -> "equals",
    "==" -> "==",
    "!=" -> "!=",
    "eq" -> "eq",
    "ne" -> "ne",
    ">" -> "<",
    "<" -> ">",
    ">=" -> "<=",
    "<=" -> ">=",
    "&&" -> "&&",
    "||" -> "||"
  )
}
