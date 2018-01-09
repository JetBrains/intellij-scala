package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
  * @author Ksenia.Sautina
  * @since 5/12/12
  */
class DeMorganLawIntention extends PsiElementBaseIntentionAction {

  import DeMorganLawIntention._
  import IntentionUtils._

  def getFamilyName: String = familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
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

    val ScInfixExpr(left, operation, right) = infixExpr
    val text = s"${negate(left)} ${Replacement(operation.refName)} ${negate(right)}"

    negateAndValidateExpression(infixExpr, text)(project, editor)
  }
}

object DeMorganLawIntention {
  def familyName = "DeMorgan Law"

  private val Replacement = Map("" +
    "&&" -> "||",
    "||" -> "&&"
  )
}
