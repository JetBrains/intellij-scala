package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ModifiableValue}
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

import java.util.concurrent.Callable

private final class ScalaLazyResolveEvaluator(
  codeFragment: ScalaCodeFragment,
  position: SourcePosition
) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value =
    createEvaluator(context.getProject).evaluate(context)

  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = {
    val evaluator = createEvaluator(context.getProject)
    val value = evaluator.evaluate(context)
    val modifier = evaluator.getModifier
    new ModifiableValue(value, modifier)
  }

  private def createEvaluator(project: Project): ExpressionEvaluator = {
    val callable: Callable[ExpressionEvaluator] = () => ScalaEvaluatorBuilder.build(codeFragment, position)
    ReadAction.nonBlocking(callable).expireWhen(() => project.isDisposed).executeSynchronously()
  }
}
