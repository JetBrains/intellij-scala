package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi.{ClassType, ObjectReference, Value}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

import scala.jdk.CollectionConverters._
import scala.util.Try

class NewValueClassInstanceEvaluator(typeEvaluator: ScalaTypeEvaluator, param: Evaluator) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val debugProcess = context.getDebugProcess
    val ct = typeEvaluator.evaluate(context) match {
      case c: ClassType => c
      case _ =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"))
    }

    val ctor = Try(ct.methodsByName("<init>").get(0))
      .getOrElse(throw EvaluationException(ScalaBundle.message("error.cannot.resolve.value.class.primary.constructor", ct)))

    val arg = Option(param.evaluate(context)).collect { case v: Value => v }.orNull

    debugProcess.newInstance(context, ct, ctor, List(arg).asJava)
  }
}
