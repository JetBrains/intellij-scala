package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.openapi.diagnostic.Logger
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.psi.PsiType
import com.intellij.debugger.DebuggerBundle
import com.sun.jdi._
import java.util.LinkedList
import com.intellij.debugger.engine.evaluation.expression.{TypeEvaluator, Modifier, Evaluator}
import com.intellij.debugger.engine.evaluation.{EvaluationContextImpl, EvaluateExceptionUtil}

/**
 * User: Alexander Podkhalyuzin
 * Date: 07.11.11
 */
class ScalaInstanceofEvaluator(operandEvaluator: Evaluator, typeEvaluator: TypeEvaluator) extends Evaluator {
  def getModifier: Modifier = null

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    val value: Value = operandEvaluator.evaluate(context).asInstanceOf[Value]
    if (value == null) {
      return DebuggerUtilsEx.createValue(context.getDebugProcess.getVirtualMachineProxy, PsiType.BOOLEAN.getPresentableText, false)
    }
    if (!(value.isInstanceOf[ObjectReference])) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.object.reference.expected"))
    }
    try {
      val refType: ReferenceType = typeEvaluator.evaluate(context).asInstanceOf[ReferenceType]
      val classObject: ClassObjectReference = refType.classObject
      val classRefType: ClassType = classObject.referenceType.asInstanceOf[ClassType]
      val method: Method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z")
      val args: java.util.List[Object] = new LinkedList[Object]
      args.add((value.asInstanceOf[ObjectReference]).referenceType.classObject)
      context.getDebugProcess.invokeMethod(context, classObject, method, args)
    }
    catch {
      case e: Exception => {
        throw EvaluateExceptionUtil.createEvaluateException(e)
      }
    }
  }
}