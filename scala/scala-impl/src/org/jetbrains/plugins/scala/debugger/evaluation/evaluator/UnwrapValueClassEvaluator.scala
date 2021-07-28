package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.JVMName
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, IdentityEvaluator}
import com.sun.jdi.ObjectReference

case class UnwrapValueClassEvaluator(instanceEvaluator: Evaluator,
                                     className: JVMName,
                                     fieldName: String,
                                     fieldIsPrivate: Boolean) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    instanceEvaluator.evaluate(context) match {
      case objRef: ObjectReference if objRef.referenceType().name() == className.getName(context.getDebugProcess) =>
        ScalaFieldEvaluator(new IdentityEvaluator(objRef), fieldName, fieldIsPrivate).evaluate(context)
      case value => value
    }
  }
}
