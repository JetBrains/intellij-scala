package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
  * @author Ksenia.Sautina
  * @since 5/13/12
  */
class NegateComparisonIntention extends PsiElementBaseIntentionAction {

  import IntentionUtils._
  import NegateComparisonIntention._

  def getFamilyName: String = familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName
    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        setText(s"Negate '$refName' to $replacement'")
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val ScInfixExpr(ElementText(baseText), operation, ElementText(argumentText)) = infixExpr
    val text = s"$baseText ${Replacement(operation.refName)} $argumentText"
    negateAndValidateExpression(infixExpr, text)(project, editor)
  }
}


object NegateComparisonIntention {

  def familyName = "Negate comparison"

  private val Replacement = Map(
    "==" -> "!=",
    "!=" -> "==",
    ">" -> "<=",
    "<" -> ">=",
    ">=" -> "<",
    "<=" -> ">"
  )
}
