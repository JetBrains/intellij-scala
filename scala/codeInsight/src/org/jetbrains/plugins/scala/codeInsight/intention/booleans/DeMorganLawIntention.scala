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
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

final class DeMorganLawIntention extends PsiElementBaseIntentionAction {

  import DeMorganLawIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName
    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        setText(ScalaCodeInsightBundle.message("replace.refname.with.replacement", refName, replacement))
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val infixExpr@ScInfixExpr(_, op, _) = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    val targetOpName = op.refName
    val upperMostInfixExpr: ScInfixExpr = infixExpr
        .withParents
        .takeWhile(_.is[ScInfixExpr])
        .filterByType[ScInfixExpr]
        .takeWhile(_.operation.refName == targetOpName)
        .lastOption.get
    if (upperMostInfixExpr == null || !upperMostInfixExpr.isValid) return

    def inner(expr: ScExpression): String = expr match {
      case ScInfixExpr(left, op, right) if op.refName == targetOpName =>
        s"${inner(left)} ${Replacement(targetOpName)} ${inner(right)}"
      case _ => negate(expr)
    }

    negateAndValidateExpression(upperMostInfixExpr, inner(upperMostInfixExpr))(project, editor)
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.demorgan.law")
}

object DeMorganLawIntention {
  private val Replacement = Map("" +
    "&&" -> "||",
    "||" -> "&&"
  )
}
