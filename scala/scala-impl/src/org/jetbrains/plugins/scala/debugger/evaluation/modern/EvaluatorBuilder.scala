package org.jetbrains.plugins.scala.debugger.evaluation.modern

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.expression.{BlockStatementEvaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, EvaluatorBuilder => PlatformEvaluatorBuilder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

private[debugger] object EvaluatorBuilder extends PlatformEvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val evaluator = new BlockStatementEvaluator(codeFragment.getChildren.map(createEvaluator(_, position)))
    new ExpressionEvaluatorImpl(evaluator)
  }

  private def createEvaluator(element: PsiElement, position: SourcePosition): Evaluator = element match {
    case literal: ScLiteral => LiteralEvaluator.create(literal)
    case element if element.textMatches("()") => UnitEvaluator
    case _ => throw new EvaluateException(s"Cannot evaluate expression: ${element.getText}")
  }
}
