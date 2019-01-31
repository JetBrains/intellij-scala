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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
  * @author Ksenia.Sautina
  * @since 4/23/12
  */
final class ReplaceEqualsOrEqualityInInfixExprIntention extends PsiElementBaseIntentionAction {

  import ReplaceEqualsOrEqualityInInfixExprIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName

    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        setText(s"Replace '$refName' with '$replacement'")
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val ScInfixExpr.withAssoc(ElementText(baseText), operation, ElementText(argumentText)) = infixExpr

    import infixExpr.projectContext
    val start = infixExpr.getTextRange.getStartOffset
    val newInfixExpr = createExpressionFromText(s"$baseText ${Replacement(operation.refName)} $argumentText").asInstanceOf[ScInfixExpr]
    val size = newInfixExpr.operation.nameId.getTextRange.getStartOffset -
      newInfixExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replace(newInfixExpr)
      editor.getCaretModel.moveToOffset(start + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = FamilyName
}

object ReplaceEqualsOrEqualityInInfixExprIntention {

  private[booleans] val FamilyName = "Replace equals or equality in infix expression"

  private val Replacement = Map(
    "equals" -> "==",
    "==" -> "equals"
  )
}
