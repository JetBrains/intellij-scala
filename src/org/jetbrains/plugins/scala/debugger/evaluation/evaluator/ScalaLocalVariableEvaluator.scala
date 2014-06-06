package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.jdi.{LocalVariableProxyImpl, StackFrameProxyImpl}
import com.intellij.debugger.ui.impl.watch.{LocalVariableDescriptorImpl, NodeDescriptorImpl}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.jdi.{InternalException, Type, Value}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

/**
 * User: Alefas
 * Date: 12.10.11
 */

class ScalaLocalVariableEvaluator(name: String, sourceName: String) extends Evaluator {
  import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaLocalVariableEvaluator.LOG
  private val myName: String = DebuggerUtil.withoutBackticks(name)
  private val mySourceName: String = DebuggerUtil.withoutBackticks(sourceName)
  private var myContext: EvaluationContextImpl = null
  private var myEvaluatedVariable: LocalVariableProxyImpl = null
  private var myParameterIndex: Int = -1
  private var myMethodName: String = null

  def setParameterIndex(parameterIndex: Int) {
    myParameterIndex = parameterIndex
  }

  def setMethodName(name: String) {
    myMethodName = name
  }

  private def sourceName(frameProxy: StackFrameProxyImpl) = frameProxy.location().sourceName()

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    var frameProxy: StackFrameProxyImpl = context.getFrameProxy
    lazy val threadProxy = frameProxy.threadProxy()
    if (frameProxy == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.stackframe"))
    }
    def evaluate: Option[AnyRef] = {
      def saveContextAndGetValue(framePr: StackFrameProxyImpl, local: LocalVariableProxyImpl) = {
        myEvaluatedVariable = local
        myContext = context
        val value = DebuggerUtil.unwrapScalaRuntimeObjectRef {
          frameProxy.getValue(local)
        }
        Some(value)
      }
      var local: LocalVariableProxyImpl = frameProxy.visibleVariableByName(myName)
      if (local != null) return saveContextAndGetValue(frameProxy, local)
      for (i <- 1 to 2) {
        local = frameProxy.visibleVariableByName(myName + "$" + i)
        if (local != null) return saveContextAndGetValue(frameProxy, local)
      }
      val locals = frameProxy.visibleVariables()
      import scala.collection.JavaConversions._
      for (local <- locals) {
        if (local.name().startsWith(myName + "$")) return saveContextAndGetValue(frameProxy, local)
      }
      None
    }
    def evaluateWithFrames(): AnyRef = {
      var frameIndex = frameProxy.getFrameIndex
      val lastIndex = threadProxy.frameCount() - 1
      while (frameIndex < lastIndex) {
        frameProxy = threadProxy.frame(frameIndex)
        evaluate match {
          case Some(x) if sourceName(frameProxy) == mySourceName => return x
          case _ => frameIndex += 1
        }
      }
      throw EvaluateExceptionUtil.
              createEvaluateException(DebuggerBundle.message("evaluation.error.local.variable.missing", myName))
    }

    def parameterWithIndex(index: Int, frameProxy: StackFrameProxyImpl): Option[AnyRef] = {
      def inNextFrame: Option[AnyRef] = {
        val frameIndex = frameProxy.getFrameIndex
        if (frameIndex < threadProxy.frameCount() - 1) {
          parameterWithIndex(myParameterIndex, threadProxy.frame(frameIndex + 1))
        } else None
      }
      if (frameProxy == null || index < 0) None
      else {
        val frameMethodName = frameProxy.location().method().name()
        val frameSourceName = sourceName(frameProxy)
        if ((myMethodName == null && mySourceName == null) || (frameMethodName.startsWith(myMethodName) && mySourceName == frameSourceName)) {
          try {
            val values = frameProxy.getArgumentValues
            if (values != null && !values.isEmpty && index >= 0 && index < values.size()) {
              Some(values.get(index))
            } else {
              None
            }
          }
          catch {case ignore: InternalException => inNextFrame}
        } else inNextFrame
      }
    }

    try {
      evaluate.getOrElse(
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.local.variable.missing", myName)))
    }
    catch {
      case e: EvaluateException =>
        try {
          parameterWithIndex(myParameterIndex ,frameProxy).getOrElse(evaluateWithFrames())
        }
        catch {
          case e: EvaluateException =>
            myEvaluatedVariable = null
            myContext = null
            throw e
        }
    }
  }

  def getModifier: Modifier = {
    var modifier: Modifier = null
    if (myEvaluatedVariable != null && myContext != null) {
      modifier = new Modifier {
        def canInspect: Boolean = true
        def canSetValue: Boolean = true
        def setValue(value: Value) {
          val frameProxy: StackFrameProxyImpl = myContext.getFrameProxy
          try {
            frameProxy.setValue(myEvaluatedVariable, value)
          }
          catch {
            case e: EvaluateException =>
              LOG.error(e)
          }
        }
        def getExpectedType: Type = {
          try {
            myEvaluatedVariable.getType
          }
          catch {
            case e: EvaluateException =>
              LOG.error(e)
              null
          }
        }
        def getInspectItem(project: Project): NodeDescriptorImpl = {
          new LocalVariableDescriptorImpl(project, myEvaluatedVariable)
        }
      }
    }
    modifier
  }
}

object ScalaLocalVariableEvaluator {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaLocalVariableEvaluator")
}