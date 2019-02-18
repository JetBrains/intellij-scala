package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class CanonizeBlockArgument extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScMethodCall(expression, Seq(block: ScBlockExpr)) =>
      e.replace(code"$expression(${block.asSimpleExpression.getOrElse(block)})")

    case ScInfixExpr(_, _, block: ScBlockExpr) =>
      block.replace(code"(${block.asSimpleExpression.getOrElse(block)})")
  }
}
