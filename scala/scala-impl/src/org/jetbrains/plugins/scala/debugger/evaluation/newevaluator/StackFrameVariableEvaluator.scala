package org.jetbrains.plugins.scala.debugger.evaluation.newevaluator

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.jdi.{LocalVariableProxyImpl, ThreadReferenceProxyImpl}
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.Project
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.extensions.ObjectExt

private[evaluation] abstract class StackFrameVariableEvaluator extends Evaluator {

  protected val variableName: String

  protected val isModifiable: Boolean

  private[this] var localVariable: LocalVariableProxyImpl = _

  private[this] var evaluationContext: EvaluationContextImpl = _

  override def evaluate(context: EvaluationContextImpl): Value = {
    var frameProxy = context.getFrameProxy
    if (frameProxy eq null) {
      throw EvaluationException(ScalaBundle.message("error.local.variable.stack.frame.unavailable", variableName))
    }

    var threadProxy: ThreadReferenceProxyImpl = null
    var lastFrameIndex = -1

    while (frameProxy ne null) {
      try {
        val local = frameProxy.visibleVariableByName(variableName)
        if (local ne null) {
          localVariable = local
          evaluationContext = context
          return frameProxy.getValue(local)
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

    throw EvaluationException(ScalaBundle.message("error.local.variable.cannot.find.variable", variableName))
  }

  override def getModifier: Modifier =
    if (localVariable eq null) null
    else new Modifier {
      override def canInspect: Boolean = true

      override def canSetValue: Boolean = isModifiable

      override def setValue(value: Value): Unit = {
        val frameProxy = evaluationContext.getFrameProxy
        if (frameProxy eq null) {
          throw EvaluationException(ScalaBundle.message("error.local.variable.stack.frame.unavailable", variableName))
        }
        frameProxy.setValue(localVariable, value)
      }

      override def getExpectedType: Type = localVariable.getType

      override def getInspectItem(project: Project): NodeDescriptor =
        new LocalVariableDescriptorImpl(project, localVariable) {
          override def canSetValue: Boolean = isModifiable
        }
    }
}
