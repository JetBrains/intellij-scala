package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi._

import scala.annotation.tailrec
import scala.util.Try

private[evaluation] final class LocalVariableEvaluator(variableName: String, methodName: String) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    val frameProxy = context.getFrameProxy
    val threadProxy = frameProxy.threadProxy()
    val frameCount = threadProxy.frameCount()

    @tailrec
    def loop(frameIndex: Int): Value =
      if (frameIndex >= frameCount)
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.local.variable.missing", variableName))
      else {
        val stackFrame = threadProxy.frame(frameIndex)
        Try(stackFrame.location().method().name())
          .toOption
          .filter(_ == methodName)
          .map(_ => stackFrame.visibleValueByName(variableName)) match {
          case Some(value) => value
          case None => loop(frameIndex + 1)
        }
      }

    loop(frameProxy.getFrameIndex)
  }
}
