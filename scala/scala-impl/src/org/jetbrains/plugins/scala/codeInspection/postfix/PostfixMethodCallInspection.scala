package org.jetbrains.plugins.scala
package codeInspection
package postfix

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, childOf}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class PostfixMethodCallInspection extends AbstractInspection() {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case pexpr: ScPostfixExpr if !safe(pexpr) =>
      holder.registerProblem(pexpr, getDisplayName, new AddDotFix(pexpr))
  }

  private def safe(pexpr: ScPostfixExpr): Boolean = {
    pexpr.getContext match {
      case _: ScParenthesisedExpr => true
      case _: ScArgumentExprList => true
      case (_: ScAssignment) childOf (_: ScArgumentExprList) => true //named arguments
      case _ =>
        val next = pexpr.getNextSiblingNotWhitespace
        if (next == null) return false
        val nextNode = next.getNode
        if (nextNode == null) return false
        nextNode.getElementType == ScalaTokenTypes.tSEMICOLON
    }
  }
}

class AddDotFix(pexpr: ScPostfixExpr) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("add.dot.to.method.call"), pexpr) {

  override protected def doApplyFix(postfix: ScPostfixExpr)
                                   (implicit project: Project): Unit = {
    val expr = ScalaPsiElementFactory.createEquivQualifiedReference(postfix)
    postfix.replaceExpression(expr, removeParenthesis = true)
  }
}
