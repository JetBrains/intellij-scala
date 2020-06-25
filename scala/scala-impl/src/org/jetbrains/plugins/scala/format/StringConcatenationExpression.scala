package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}

object StringConcatenationExpression {
  def unapply(element: PsiElement): Option[(ScExpression, ScExpression)] = element match {
    case exp@ScSugarCallExpr(left, op, Seq(right)) if op.textMatches("+") && isString(exp) =>
      Some(left -> right)
    case _ =>
      None
  }

  private def isString(exp: ScExpression): Boolean = exp.`type`().toOption match {
    case Some(ScDesignatorType(element)) => element.name == "String"
    case Some(ScProjectionType(ScDesignatorType(predef), ta: ScTypeAlias)) => predef.name == "Predef" && ta.name == "String"
    case _ => false
  }
}
