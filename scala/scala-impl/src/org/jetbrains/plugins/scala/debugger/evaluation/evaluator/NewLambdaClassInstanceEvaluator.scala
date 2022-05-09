package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, TypeEvaluator}
import com.sun.jdi._

import scala.jdk.CollectionConverters._

private[evaluation] final class NewLambdaClassInstanceEvaluator(classTypeEvaluator: TypeEvaluator, params: Evaluator*)
  extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val debugProcess = context.getDebugProcess
    classTypeEvaluator.evaluate(context) match {
      case ct: ClassType =>
        val paramCount = params.size
        ct.allMethods().asScala.find { m =>
          m.name() == "<init>" && m.arguments().size() == paramCount
        }.map { method =>
          val evaluatedParams = params.map(_.evaluate(context).asInstanceOf[Value])
          debugProcess.newInstance(context, ct, method, evaluatedParams.asJava)
        }.getOrElse {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", s"constructor with $paramCount parameters"))
        }

      case _ => throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"))
    }
  }
}
