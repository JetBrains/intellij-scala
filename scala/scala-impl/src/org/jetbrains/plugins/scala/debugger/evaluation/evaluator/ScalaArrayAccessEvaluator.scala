package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.ui.impl.watch.{ArrayElementDescriptorImpl, NodeDescriptorImpl}
import com.intellij.openapi.project.Project
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

class ScalaArrayAccessEvaluator(arrayReferenceEvaluator: Evaluator, indexEvaluator: Evaluator) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    myEvaluatedIndex = 0
    myEvaluatedArrayReference = null
    val indexValue: Value = indexEvaluator.evaluate(context).asInstanceOf[Value]
    val arrayValue: Value = arrayReferenceEvaluator.evaluate(context).asInstanceOf[Value]
    if (!arrayValue.isInstanceOf[ArrayReference]) {
      throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.array.reference.expected"))
    }
    myEvaluatedArrayReference = arrayValue.asInstanceOf[ArrayReference]
    if (!DebuggerUtils.isInteger(indexValue)) {
      throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.invalid.index.expression"))
    }
    myEvaluatedIndex = indexValue.asInstanceOf[PrimitiveValue].intValue
    try {
      myEvaluatedArrayReference.getValue(myEvaluatedIndex)
    }
    catch {
      case e: Exception =>
        throw EvaluationException(e)
    }
  }

  override def getModifier: Modifier = {
    var modifier: Modifier = null
    if (myEvaluatedArrayReference != null) {
      modifier = new Modifier {
        override def canInspect: Boolean = true
        override def canSetValue: Boolean = true
        override def setValue(value: Value): Unit = {
          myEvaluatedArrayReference.setValue(myEvaluatedIndex, value)
        }
        override def getExpectedType: Type = {
          try {
            val tp: ArrayType = myEvaluatedArrayReference.referenceType.asInstanceOf[ArrayType]
            tp.componentType
          }
          catch {
            case e: ClassNotLoadedException =>
              throw EvaluationException(e)
          }
        }
        override def getInspectItem(project: Project): NodeDescriptorImpl = {
          new ArrayElementDescriptorImpl(project, myEvaluatedArrayReference, myEvaluatedIndex)
        }
      }
    }
    modifier
  }

  private var myEvaluatedArrayReference: ArrayReference = null
  private var myEvaluatedIndex: Int = 0
}