package org.jetbrains.plugins.scala.debugger.evaluation
package newevaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, TypeEvaluator}
import com.intellij.debugger.engine.{DebuggerUtils, JVMName, JVMNameUtil}
import com.sun.jdi._

import scala.jdk.CollectionConverters._

private[evaluation] class NewClassInstanceEvaluator(classTypeEvaluator: TypeEvaluator, constructorSignature: JVMName)
  extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val debugProcess = context.getDebugProcess
    classTypeEvaluator.evaluate(context) match {
      case ct: ClassType =>
        val method = DebuggerUtils.findMethod(ct, JVMNameUtil.CONSTRUCTOR_NAME, constructorSignature.getName(debugProcess))
        if (method eq null) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", constructorSignature.getDisplayName(debugProcess)))
        }

        debugProcess.newInstance(context, ct, method, Seq.empty.asJava)

      case _ => throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"))
    }
  }
}
