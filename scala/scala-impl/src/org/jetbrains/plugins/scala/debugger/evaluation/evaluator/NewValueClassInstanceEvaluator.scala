package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil.createEvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi.{ClassType, ObjectReference, Value}

import scala.jdk.CollectionConverters._
import scala.util.Try

class NewValueClassInstanceEvaluator(typeEvaluator: ScalaTypeEvaluator, param: Evaluator) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val debugProcess = context.getDebugProcess
    val classTypeInstance = typeEvaluator.evaluate(context) match {
      case ct: ClassType => ct
      case _ =>
        throw createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"))
    }

    val ctor =
      Try(classTypeInstance.methodsByName("<init>").get(0))
        .getOrElse(throw createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", "")))

    val arg = Option(param.evaluate(context)).collect { case v: Value => v }.orNull

    debugProcess.newInstance(context, classTypeInstance, ctor, List(arg).asJava)
  }
}
