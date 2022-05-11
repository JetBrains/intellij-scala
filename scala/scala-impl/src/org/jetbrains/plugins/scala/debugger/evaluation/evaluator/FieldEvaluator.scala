package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.JVMName
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi._

import scala.jdk.CollectionConverters._

private[evaluation] final class FieldEvaluator(instanceEvaluator: Evaluator, fieldName: String, typeName: JVMName) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    instanceEvaluator.evaluate(context) match {
      case ref: ObjectReference =>
        ref.referenceType().allFields().asScala.find { f =>
          val name = f.name()
          val tpe = f.`type`()
          (name ne null) && (name == fieldName || name.endsWith(s"$$$$$fieldName")) && tpe.name() == typeName.getName(context.getDebugProcess)
        }.map(ref.getValue).getOrElse(throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.field", fieldName)))

      case _ => throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.field", fieldName))
    }
  }
}
