package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi._

import scala.annotation.tailrec
import scala.util.Try

private final class LocalVariableEvaluator private(variableName: String, methodName: String)
  extends ValueEvaluator {
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
        Try(stackFrame.location())
          .collect { case loc if filterLocation(loc) => Option(stackFrame.visibleVariableByName(variableName)) }
          .toOption
          .flatten match {
          case Some(local) => stackFrame.getValue(local)
          case None => loop(frameIndex + 1)
        }
      }

    loop(frameProxy.getFrameIndex)
  }

  private def filterLocation(location: Location): Boolean =
    location.method().name().contains(methodName) || location.declaringType().name().contains(methodName)
}

private[evaluation] object LocalVariableEvaluator {
  private[evaluation] def inMethod(variableName: String, methodName: String): ValueEvaluator =
    new LocalVariableEvaluator(variableName, methodName)
}
