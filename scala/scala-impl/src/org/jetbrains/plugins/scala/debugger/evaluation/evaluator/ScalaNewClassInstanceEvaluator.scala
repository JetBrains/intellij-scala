package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import java.util

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.engine.{DebuggerUtils, JVMName}
import com.sun.jdi.{ClassType, ObjectReference, Value}

/**
 * User: Alefas
 * Date: 17.10.11
 *
 * converted from java verbatim
 */
class ScalaNewClassInstanceEvaluator(val myClassTypeEvaluator: Evaluator, val myConstructorSignature: JVMName, val myParamsEvaluators: Array[Evaluator]) extends Evaluator {
  @throws[EvaluateException]
  override def evaluate(context: EvaluationContextImpl) = {
    val debugProcess = context.getDebugProcess
    val obj = myClassTypeEvaluator.evaluate(context)
    if (!obj.isInstanceOf[ClassType]) throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"))
    val classType = obj.asInstanceOf[ClassType]
    // find constructor
    val method = DebuggerUtils.findMethod(classType, "<init>", myConstructorSignature.getName(debugProcess))
    if (method == null) throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", myConstructorSignature.getDisplayName(debugProcess)))
    // evaluate arguments
    var arguments: util.ArrayList[Value] = null
    if (myParamsEvaluators != null) {
      arguments = new util.ArrayList[Value](myParamsEvaluators.length)
      for (evaluator <- myParamsEvaluators) {
        val result = evaluator.evaluate(context)
        if (result.isInstanceOf[Option[_]]) if (result.asInstanceOf[Option[_]].isDefined) arguments.add(result.asInstanceOf[Value])
        else arguments.add(result.asInstanceOf[Value])
      }
    }
    else arguments  = new util.ArrayList[Value]
    var objRef: ObjectReference = null
    try objRef = debugProcess.newInstance(context, classType, method, arguments)
    catch {
      case e: EvaluateException =>
        throw EvaluateExceptionUtil.createEvaluateException(e)
    }
    objRef
  }

  override def getModifier = null
}