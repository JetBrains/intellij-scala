package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.watch.{ArrayElementDescriptorImpl, NodeDescriptorImpl}
import com.intellij.openapi.project.Project
import com.sun.jdi._

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.11.11
 */
class ScalaArrayAccessEvaluator(arrayReferenceEvaluator: Evaluator, indexEvaluator: Evaluator) extends Evaluator {
  def evaluate(context: EvaluationContextImpl): AnyRef = {
    myEvaluatedIndex = 0
    myEvaluatedArrayReference = null
    val indexValue: Value = indexEvaluator.evaluate(context).asInstanceOf[Value]
    val arrayValue: Value = arrayReferenceEvaluator.evaluate(context).asInstanceOf[Value]
    if (!(arrayValue.isInstanceOf[ArrayReference])) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.array.reference.expected"))
    }
    myEvaluatedArrayReference = arrayValue.asInstanceOf[ArrayReference]
    if (!DebuggerUtils.isInteger(indexValue)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.index.expression"))
    }
    myEvaluatedIndex = (indexValue.asInstanceOf[PrimitiveValue]).intValue
    try {
      myEvaluatedArrayReference.getValue(myEvaluatedIndex)
    }
    catch {
      case e: Exception => {
        throw EvaluateExceptionUtil.createEvaluateException(e)
      }
    }
  }

  def getModifier: Modifier = {
    var modifier: Modifier = null
    if (myEvaluatedArrayReference != null) {
      modifier = new Modifier {
        def canInspect: Boolean = true
        def canSetValue: Boolean = true
        def setValue(value: Value) {
          myEvaluatedArrayReference.setValue(myEvaluatedIndex, value)
        }
        def getExpectedType: Type = {
          try {
            val tp: ArrayType = myEvaluatedArrayReference.referenceType.asInstanceOf[ArrayType]
            tp.componentType
          }
          catch {
            case e: ClassNotLoadedException => {
              throw EvaluateExceptionUtil.createEvaluateException(e)
            }
          }
        }
        def getInspectItem(project: Project): NodeDescriptorImpl = {
          new ArrayElementDescriptorImpl(project, myEvaluatedArrayReference, myEvaluatedIndex)
        }
      }
    }
    modifier
  }

  private var myEvaluatedArrayReference: ArrayReference = null
  private var myEvaluatedIndex: Int = 0
}