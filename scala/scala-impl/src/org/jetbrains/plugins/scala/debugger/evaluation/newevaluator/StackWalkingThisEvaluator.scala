package org.jetbrains.plugins.scala.debugger.evaluation
package newevaluator

import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._

private[evaluation] class StackWalkingThisEvaluator(fqn: String) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    var frameProxy = context.getFrameProxy
    if (frameProxy eq null) {
      throw EvaluationException(ScalaBundle.message("error.local.variable.stack.frame.unavailable", "this"))
    }

    var threadProxy: ThreadReferenceProxyImpl = null
    var lastFrameIndex = -1

    while (frameProxy ne null) {
      try {
        val thisRef = frameProxy.thisObject()
        if ((thisRef ne null) && thisRef.referenceType().name() == fqn) {
          return thisRef
        }
      } catch {
        case e: EvaluateException if e.getCause.is[AbsentInformationException] =>
      }

      if (threadProxy eq null) {
        threadProxy = frameProxy.threadProxy()
        lastFrameIndex = threadProxy.frameCount() - 1
      }
      val currentFrameIndex = frameProxy.getFrameIndex
      if (currentFrameIndex < lastFrameIndex) {
        frameProxy = threadProxy.frame(currentFrameIndex + 1)
      } else {
        frameProxy = null
      }
    }

    throw EvaluationException(ScalaBundle.message("error.local.variable.cannot.find.variable", "this"))
  }
}
