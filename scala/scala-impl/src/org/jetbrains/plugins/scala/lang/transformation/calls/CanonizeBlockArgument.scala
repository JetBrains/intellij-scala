package org.jetbrains.plugins.scala.lang.transformation.calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation.AbstractTransformer
import org.jetbrains.plugins.scala.project.ProjectContext

class CanonizeBlockArgument extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScMethodCall(expression, Seq(block: ScBlockExpr)) =>
      e.replace(code"$expression(${toSimpleExpressionOrBracedBlock(block)})")

    case ScInfixExpr(_, _, block: ScBlockExpr) =>
      block.replace(code"(${toSimpleExpressionOrBracedBlock(block)})")
  }

  override def needsReformat(e: PsiElement): Boolean = e match {
    case ScMethodCall(_, Seq(block: ScBlockExpr)) => !block.isEnclosedByBraces
    case ScInfixExpr(_, _, block: ScBlockExpr) => !block.isEnclosedByBraces
    case _ => false
  }

  private def toSimpleExpressionOrBracedBlock(block: ScBlockExpr)
                                             (implicit project: ProjectContext): ScExpression =
    block.asSimpleExpression
      .getOrElse(ScalaPsiUtil.convertBlockToBraced(block))
}
