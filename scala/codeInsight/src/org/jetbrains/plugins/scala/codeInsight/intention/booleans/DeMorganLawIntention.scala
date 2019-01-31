package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Ksenia.Sautina
  * @since 5/12/12
  */
final class DeMorganLawIntention extends PsiElementBaseIntentionAction {

  import DeMorganLawIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName
    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        setText(s"Replace '$refName' with $replacement'")
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val ScInfixExpr(base, operation, argument) = infixExpr
    val text = s"${negate(base)} ${Replacement(operation.refName)} ${negate(argument)}"

    negateAndValidateExpression(infixExpr, text)(project, editor)
  }

  override def getFamilyName: String = FamilyName
}

object DeMorganLawIntention {

  private[booleans] val FamilyName = "DeMorgan Law"

  private val Replacement = Map("" +
    "&&" -> "||",
    "||" -> "&&"
  )
}
