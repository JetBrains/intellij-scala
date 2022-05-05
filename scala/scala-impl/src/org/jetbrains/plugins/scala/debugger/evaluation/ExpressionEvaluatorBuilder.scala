package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.debugger.evaluation.newevaluator.{LocalValEvaluator, LocalVarEvaluator}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

private[debugger] object ExpressionEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val element = codeFragment.asInstanceOf[ScalaCodeFragment].children.toList.head
    new ExpressionEvaluatorImpl(buildEvaluator(element))
  }

  private def buildEvaluator(element: PsiElement): Evaluator =
    element match {
      case expr: ScReferenceExpression => buildReferenceExpressionEvaluator(expr)
      case _ => throw EvaluationException(s"Cannot evaluate expression $element")
    }

  private def buildReferenceExpressionEvaluator(expression: ScReferenceExpression): Evaluator =
    expression.resolve() match {
      case rp: ScReferencePattern =>
        val constructor = if (rp.isVar) new LocalVarEvaluator(_) else new LocalValEvaluator(_)
        constructor(rp.name)
      case _ =>
        ???
    }
}
