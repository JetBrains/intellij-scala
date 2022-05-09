package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.LambdaExpressionEvaluator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

private object ExpressionEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val element = codeFragment.asInstanceOf[ScalaCodeFragment].children.toList.head
    new ExpressionEvaluatorImpl(buildEvaluator(element, position))
  }

  private def buildEvaluator(element: PsiElement, position: SourcePosition): Evaluator = element match {
    case _: ScFunctionExpr => new LambdaExpressionEvaluator(position.getElementAt)
  }
}
