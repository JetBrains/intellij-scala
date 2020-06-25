package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import java.util

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier, TypeEvaluator}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.psi.PsiType
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

/**
  * User: Alexander Podkhalyuzin
  * Date: 07.11.11
  */
class ScalaInstanceofEvaluator(operandEvaluator: Evaluator, typeEvaluator: TypeEvaluator) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    val value: Value = operandEvaluator.evaluate(context).asInstanceOf[Value]
    if (value == null) {
      return DebuggerUtilsEx.createValue(context.getDebugProcess.getVirtualMachineProxy, PsiType.BOOLEAN.getPresentableText(), false)
    }
    if (!value.isInstanceOf[ObjectReference]) {
      throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.object.reference.expected"))
    }
    try {
      val refType: ReferenceType = typeEvaluator.evaluate(context).asInstanceOf[ReferenceType]
      val classObject: ClassObjectReference = refType.classObject
      val classRefType: ClassType = classObject.referenceType.asInstanceOf[ClassType]
      val method: Method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z")
      val args: java.util.List[Value] = new util.LinkedList[Value]
      args.add(value.asInstanceOf[ObjectReference].referenceType.classObject)
      context.getDebugProcess.invokeMethod(context, classObject, method, args)
    }
    catch {
      case e: Exception =>
        throw EvaluationException(e)
    }
  }
}