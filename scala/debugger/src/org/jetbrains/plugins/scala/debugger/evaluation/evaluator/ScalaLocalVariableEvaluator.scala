package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.expression.{ModifiableEvaluator, ModifiableValue, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.jdi.{LocalVariableProxyImpl, StackFrameProxyImpl}
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaLocalVariableEvaluator.{EvaluationResult, MyModifier}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

/**
 * Follows the implementation details of `com.intellij.debugger.engine.evaluation.expression.LocalVariableEvaluator`
 * but is adapted for Scala.
 */
class ScalaLocalVariableEvaluator(
  name: String,
  sourceName: String,
  parameterIndex: Option[Int] = None,
  methodName: Option[String] = None
) extends ModifiableEvaluator {
  private val depthOfSearch = 20

  private val myName: String = DebuggerUtil.withoutBackticks(name)
  private val mySourceName: String = DebuggerUtil.withoutBackticks(sourceName)

  private def sourceName(frameProxy: StackFrameProxyImpl) =
    try frameProxy.location().sourceName()
    catch {
      case _: AbsentInformationException => ""
    }

  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = {

    def proxyAndValue(framePr: StackFrameProxyImpl, local: LocalVariableProxyImpl): EvaluationResult = {
      val value = framePr.getValue(local)
      EvaluationResult(Some(local), value)
    }

    val startFrame = context.getFrameProxy
    val threadProxy = startFrame.threadProxy()
    val startIndex = startFrame.getFrameIndex
    val lastIndex = threadProxy.frameCount() - 1
    val upperBound = Math.min(lastIndex, startIndex + depthOfSearch)

    def evaluateWithFrames(evaluationStrategy: StackFrameProxyImpl => Option[EvaluationResult]): Option[EvaluationResult] = {
      var frameIndex = startIndex
      while (frameIndex <= upperBound) {
        val frameProxy = threadProxy.frame(frameIndex)
        if (frameProxy != null) {
          if (sourceName(frameProxy) == mySourceName) {
            try {
              val res = evaluationStrategy(frameProxy)
              if (res.nonEmpty) {
                return res
              }
            } catch {
              case _: EvaluateException =>
            }
          }
        }
        frameIndex += 1
      }
      None
    }

    def withSimpleName(frameProxy: StackFrameProxyImpl): Option[EvaluationResult] = {
      val local: LocalVariableProxyImpl = frameProxy.visibleVariableByName(myName)
      Option(local).map(proxyAndValue(frameProxy, _))
    }

    def withDollar(frameProxy: StackFrameProxyImpl): Option[EvaluationResult] = {
      var i = 1
      while (i <= 2) {
        val local = frameProxy.visibleVariableByName(myName + "$" + i)
        if (local != null) {
          return Some(proxyAndValue(frameProxy, local))
        }
        i += 1
      }
      val locals = frameProxy.visibleVariables().iterator()
      while (locals.hasNext) {
        val local = locals.next()
        if (local.name().startsWith(myName + "$")) {
          return Some(proxyAndValue(frameProxy, local))
        }
      }
      None
    }

    def parameterByIndex(frameProxy: StackFrameProxyImpl): Option[EvaluationResult] = {
      parameterIndex match {
        case Some(idx) =>
          val frameMethodName = frameProxy.location().method().name()

          def argumentValueAtIndex(): Option[EvaluationResult] =
            try {
              val values = frameProxy.getArgumentValues
              if (values.isEmpty) return None
              val paramIdx = if (idx < 0) values.size() + idx else idx
              val value = values.get(paramIdx)
              Some(EvaluationResult(evaluatedVariableProxy = None, value = value))
            } catch {
              case _: InternalException => None
            }

          methodName match {
            case None => argumentValueAtIndex()
            case Some(mn) if frameMethodName.startsWith(mn) => argumentValueAtIndex()
            case _ => None
          }

        case None => None
      }
    }

    if (context.getFrameProxy == null) {
      throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.no.stackframe"))
    }

    val result = evaluateWithFrames(withSimpleName)
      .orElse(evaluateWithFrames(parameterByIndex))
      .orElse(evaluateWithFrames(withDollar))

    result match {
      case Some(EvaluationResult(Some(proxy), value)) =>
        val modifier = new MyModifier(context, proxy)
        new ModifiableValue(value, modifier)
      case Some(EvaluationResult(None, value)) =>
        new ModifiableValue(value, null)
      case None =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.local.variable.missing", myName))
    }
  }
}

private object ScalaLocalVariableEvaluator {
  private val LOG: Logger = Logger.getInstance(classOf[ScalaLocalVariableEvaluator])

  private final case class EvaluationResult(evaluatedVariableProxy: Option[LocalVariableProxyImpl], value: Value)

  private final class MyModifier(context: EvaluationContextImpl, evaluatedVariable: LocalVariableProxyImpl) extends Modifier {
    override def canInspect: Boolean = true
    override def canSetValue: Boolean = true
    override def setValue(value: Value): Unit = {
      val frameProxy = context.getFrameProxy
      try {
        if (DebuggerUtil.isScalaRuntimeRef(evaluatedVariable.getType.name())) {
          frameProxy.getValue(evaluatedVariable) match {
            case objRef: ObjectReference =>
              val field = DebuggerUtil.runtimeRefField(objRef.referenceType())
              field.foreach(objRef.setValue(_, value))
            case _ =>
              frameProxy.setValue(evaluatedVariable, value)
          }
        } else {
          frameProxy.setValue(evaluatedVariable, value)
        }
      }
      catch {
        case e: EvaluateException =>
          LOG.error(e)
      }
    }

    override def getExpectedType: Type =
      try evaluatedVariable.getType
      catch {
        case e: EvaluateException =>
          LOG.error(e)
          null
      }

    override def getInspectItem(project: Project): NodeDescriptor =
      new LocalVariableDescriptorImpl(project, evaluatedVariable)
  }
}
