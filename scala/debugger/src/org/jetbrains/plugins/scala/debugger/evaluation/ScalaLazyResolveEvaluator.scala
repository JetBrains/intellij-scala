package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ModifiableValue}
import com.intellij.openapi.application.ReadAction
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

import java.util.concurrent.Callable

private final class ScalaLazyResolveEvaluator(
  codeFragment: ScalaCodeFragment,
  position: SourcePosition
) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    val callable: Callable[Value] = () => ScalaEvaluatorBuilder.build(codeFragment, position).evaluate(context)
    val project = context.getProject
    ReadAction.nonBlocking(callable).expireWhen(() => project.isDisposed).executeSynchronously()
  }

  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = {
    val expressionEvaluator = ScalaEvaluatorBuilder.build(codeFragment, position)
    val callable: Callable[Value] = () => expressionEvaluator.evaluate(context)
    val project = context.getProject
    val value = ReadAction.nonBlocking(callable).expireWhen(() => project.isDisposed).executeSynchronously()
    val modifier = expressionEvaluator.getModifier
    new ModifiableValue(value, modifier)
  }
}
