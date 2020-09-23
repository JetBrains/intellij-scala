package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import java.util
import java.util.Collections

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil.createEvaluateException
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.{DebuggerUtils, JVMName}
import com.intellij.debugger.impl.{DebuggerUtilsEx, DebuggerUtilsImpl}
import com.jetbrains.jdi.MethodImpl
import com.sun.jdi.{ClassType, Method, ObjectReference, Value}
import com.sun.tools.jdi.MethodImpl

/**
 * User: Alefas
 * Date: 17.10.11
 *
 * converted from java verbatim
 */
class ScalaNewClassInstanceEvaluator(val myClassTypeEvaluator: Evaluator, val myConstructorSignature: JVMName, val myParamsEvaluators: Array[Evaluator]) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    val debugProcess = context.getDebugProcess
    val classTypeInstance = myClassTypeEvaluator.evaluate(context) match {
      case ct: ClassType => ct
      case _ =>
        throw createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"))
    }
    val ctor = DebuggerUtils.findMethod(classTypeInstance, "<init>", myConstructorSignature.getName(debugProcess)) match {
      case m: Method => m
      case _ =>
        throw createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", myConstructorSignature.getDisplayName(debugProcess)))
    }
    val arguments: util.List[_ <: Value] = if (myParamsEvaluators != null) {
      val buffer = new util.ArrayList[Value]()
      myParamsEvaluators.map(x => x.evaluate(context) match {
        case Some(result:Value) => buffer.add(result)
        case result:Value       => buffer.add(result)
        case _ =>
      })
      buffer
    } else Collections.emptyList()

    debugProcess.newInstance(context, classTypeInstance, ctor, arguments)
  }

  override def getModifier: Modifier = null
}