package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.jdi.{StackFrameProxyImpl, LocalVariableProxyImpl}
import com.intellij.debugger.ui.impl.watch.{LocalVariableDescriptorImpl, NodeDescriptorImpl}
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluateExceptionUtil, EvaluationContextImpl}
import com.sun.jdi.{Value, Type, AbsentInformationException}
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

/**
 * User: Alefas
 * Date: 12.10.11
 */

class ScalaLocalVariableEvaluator(_name: String, fromLocalMethod: Boolean = false) extends Evaluator {
  import ScalaLocalVariableEvaluator.LOG
  private var myContext: EvaluationContextImpl = null
  private var myEvaluatedVariable: LocalVariableProxyImpl = null
  private var myParameterIndex: Int = -1

  def setParameterIndex(parameterIndex: Int) {
    myParameterIndex = parameterIndex
  }

  private val name: String = DebuggerUtil.withoutBackticks(_name)

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    var frameProxy: StackFrameProxyImpl = context.getFrameProxy
    lazy val threadProxy = frameProxy.threadProxy()
    if (frameProxy == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.stackframe"))
    }
    def evaluate: Option[AnyRef] = {
      var local: LocalVariableProxyImpl = frameProxy.visibleVariableByName(name)
      if (local != null) {
        myEvaluatedVariable = local
        myContext = context
        return Some(frameProxy.getValue(local))
      }
      for (i <- 1 to 2) {
        local = frameProxy.visibleVariableByName(name + "$" + i)
        if (local != null) {
          myEvaluatedVariable = local
          myContext = context
          return Some(frameProxy.getValue(local))
        }
      }
      val locals = frameProxy.visibleVariables()
      import scala.collection.JavaConversions._
      for (local <- locals) {
        if (local.name().startsWith(name + "$")) {
          myEvaluatedVariable = local
          myContext = context
          return Some(frameProxy.getValue(local))
        }
      }
      None
    }
    def evaluateWithFrames(): AnyRef = {
      if (fromLocalMethod) {
        var frameIndex = frameProxy.getFrameIndex
        val lastIndex = threadProxy.frameCount() - 1
        while (frameIndex < lastIndex) {
          frameProxy = threadProxy.frame(frameIndex)
          evaluate match {
            case Some(x) => return x
            case None => frameIndex += 1
          }
        }
      }
      throw EvaluateExceptionUtil.
              createEvaluateException(DebuggerBundle.message("evaluation.error.local.variable.missing", name))
    }
    def parameterWithIndex(index: Int, frameProxy: StackFrameProxyImpl): Option[AnyRef] = {
      val values = frameProxy.getArgumentValues
      if (values != null && !values.isEmpty && index >= 0 && index < values.size()) {
        Some(values.get(index))
      } else {
        None
      }
    }

    try {
      if (fromLocalMethod) {
        //this is local variable outside of local method
        try {
          parameterWithIndex(myParameterIndex ,frameProxy)
                  .getOrElse(evaluateWithFrames())
        }
        catch {
          case ignore: Exception => evaluateWithFrames()
          //Strange Unexpected JDWP error: 35 is possible here, let's try to find local by stackframe
        }
      } else {
        evaluate match {
          case Some(x) => x
          case None => throw EvaluateExceptionUtil.
                  createEvaluateException(DebuggerBundle.message("evaluation.error.local.variable.missing", name))
        }
      }
    }
    catch {
      case e: EvaluateException => {
        val param = parameterWithIndex(myParameterIndex, frameProxy)
        if (e.getCause.isInstanceOf[AbsentInformationException] && param.nonEmpty) {
          param.get
        } else {
          myEvaluatedVariable = null
          myContext = null
          throw e
        }
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
            case e: EvaluateException => {
              LOG.error(e)
            }
          }
        }
        def getExpectedType: Type = {
          try {
            myEvaluatedVariable.getType
          }
          catch {
            case e: EvaluateException => {
              LOG.error(e)
              null
            }
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