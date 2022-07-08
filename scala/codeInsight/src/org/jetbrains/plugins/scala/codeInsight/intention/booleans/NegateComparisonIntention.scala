package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

final class NegateComparisonIntention extends PsiElementBaseIntentionAction {

  import NegateComparisonIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName
    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        setText(ScalaCodeInsightBundle.message("negate.operation.to.inverse", refName, replacement))
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

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.negate.comparison")
}


object NegateComparisonIntention {
  private val Replacement = Map(
    "==" -> "!=",
    "!=" -> "==",
    ">" -> "<=",
    "<" -> ">=",
    ">=" -> "<",
    "<=" -> ">"
  )
}
