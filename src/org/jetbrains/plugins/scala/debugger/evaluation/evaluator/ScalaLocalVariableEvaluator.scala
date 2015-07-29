package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.jdi.{LocalVariableProxyImpl, StackFrameProxyImpl}
import com.intellij.debugger.ui.impl.watch.{LocalVariableDescriptorImpl, NodeDescriptorImpl}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.jdi.{InternalException, ObjectReference, Type, Value}
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
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

    def saveContextAndGetValue(framePr: StackFrameProxyImpl, local: LocalVariableProxyImpl) = {
      myEvaluatedVariable = local
      myContext = context
      Some(framePr.getValue(local))
    }

    def evaluateWithFrames(evaluationStrategy: StackFrameProxyImpl => Option[AnyRef]): Option[AnyRef] = {
      val startFrame = context.getFrameProxy
      val threadProxy = startFrame.threadProxy()
      val lastIndex = threadProxy.frameCount() - 1
      var frameIndex = startFrame.getFrameIndex
      while (frameIndex < lastIndex) {
        val frameProxy = threadProxy.frame(frameIndex)
        try {
          evaluationStrategy(frameProxy) match {
            case Some(x) if sourceName(frameProxy) == mySourceName => return Some(x)
            case _ => frameIndex += 1
          }
        }
        catch {
          case e: EvaluateException =>
            myEvaluatedVariable = null
            myContext = null
        }
      }
      None
    }

    def withSimpleName(frameProxy: StackFrameProxyImpl) : Option[AnyRef] = {
      val local: LocalVariableProxyImpl = frameProxy.visibleVariableByName(myName)
      Option(local).flatMap(saveContextAndGetValue(frameProxy, _))
    }

    def withDollar(frameProxy: StackFrameProxyImpl): Option[AnyRef] = {
      for (i <- 1 to 2) {
        val local = frameProxy.visibleVariableByName(myName + "$" + i)
        if (local != null) return saveContextAndGetValue(frameProxy, local)
      }
      val locals = frameProxy.visibleVariables()
      import scala.collection.JavaConversions._
      for (local <- locals) {
        if (local.name().startsWith(myName + "$")) return saveContextAndGetValue(frameProxy, local)
      }
      None
    }

    def parameterByIndex(frameProxy: StackFrameProxyImpl) = {
      if (frameProxy == null || myParameterIndex < 0) None
      else {
        val frameMethodName = frameProxy.location().method().name()
        val frameSourceName = sourceName(frameProxy)
        if ((myMethodName == null && mySourceName == null) || (frameMethodName.startsWith(myMethodName) && mySourceName == frameSourceName)) {
          try {
            val values = frameProxy.getArgumentValues
            if (values != null && !values.isEmpty && myParameterIndex >= 0 && myParameterIndex < values.size()) {
              Some(values.get(myParameterIndex))
            } else {
              None
            }
          }
          catch {case ignore: InternalException => None}
        } else None
      }
    }

    if (context.getFrameProxy == null) {
      throw EvaluationException(DebuggerBundle.message("evaluation.error.no.stackframe"))
    }

    val result = evaluateWithFrames(withSimpleName)
      .orElse(evaluateWithFrames(parameterByIndex))
      .orElse(evaluateWithFrames(withDollar))

    result match {
      case Some(x) => x
      case None =>
        myEvaluatedVariable = null
        myContext = null
        throw EvaluationException(DebuggerBundle.message("evaluation.error.local.variable.missing", myName))
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
            if (DebuggerUtil.isScalaRuntimeRef(myEvaluatedVariable.getType.name())) {
              frameProxy.getValue(myEvaluatedVariable) match {
                case objRef: ObjectReference =>
                  val field = objRef.referenceType().fieldByName("elem")
                  objRef.setValue(field, value)
                case _ =>
                  frameProxy.setValue(myEvaluatedVariable, value)
              }
            } else {
              frameProxy.setValue(myEvaluatedVariable, value)
            }
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