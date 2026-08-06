package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ModifiableEvaluator, ModifiableValue, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.Project
import com.sun.jdi._
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaArrayAccessEvaluator.MyModifier

/**
 * This is a Scala translation of `com.intellij.debugger.engine.evaluation.expression.ArrayAccessEvaluator`.
 */
class ScalaArrayAccessEvaluator(arrayReferenceEvaluator: Evaluator, indexEvaluator: Evaluator) extends ModifiableEvaluator {

  @NotNull
  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = {
    arrayReferenceEvaluator.evaluate(context) match {
      case evaluatedArrayReference: ArrayReference =>
        val indexValue = indexEvaluator.evaluate(context).asInstanceOf[Value]
        if (!DebuggerUtils.isInteger(indexValue)) {
          throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.index.expression"))
        }
        val evaluatedIndex = indexValue.asInstanceOf[PrimitiveValue].intValue()

        try {
          val value = evaluatedArrayReference.getValue(evaluatedIndex)
          new ModifiableValue(value, new MyModifier(evaluatedArrayReference, evaluatedIndex))
        } catch {
          case e: Exception =>
            throw EvaluateExceptionUtil.createEvaluateException(e)
        }

      case _ =>
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.array.reference.expected"))
    }
  }
}

private object ScalaArrayAccessEvaluator {
  private final class MyModifier(evaluatedArrayReference: ArrayReference, evaluatedIndex: Int) extends Modifier {
    override def canInspect: Boolean = true
    override def canSetValue: Boolean = true
    override def setValue(value: Value): Unit = {
      evaluatedArrayReference.setValue(evaluatedIndex, value)
    }
    override def getExpectedType: Type = {
      try {
        val tpe = evaluatedArrayReference.referenceType().asInstanceOf[ArrayType]
        tpe.componentType()
      } catch {
        case e: ClassNotLoadedException =>
          throw EvaluateExceptionUtil.createEvaluateException(e)
      }
    }

    override def getInspectItem(project: Project): NodeDescriptor = {
      new ArrayElementDescriptorImpl(project, evaluatedArrayReference, evaluatedIndex)
    }
  }
}
