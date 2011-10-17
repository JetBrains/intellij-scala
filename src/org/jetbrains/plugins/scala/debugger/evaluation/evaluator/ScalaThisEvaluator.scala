package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Modifier, Evaluator}
import com.intellij.debugger.DebuggerBundle
import com.sun.jdi.{Field, ObjectReference, Value}
import com.intellij.debugger.engine.evaluation.{EvaluationContextImpl, EvaluateExceptionUtil}

/**
 * User: Alefas
 * Date: 12.10.11
 */
class ScalaThisEvaluator(iterations: Int = 0) extends Evaluator {
  private def getOuterObject(objRef: ObjectReference): ObjectReference = {
    if (objRef == null) {
      return null
    }
    val list = objRef.referenceType.fields
    import scala.collection.JavaConversions._
    for (field <- list) {
      val name: String = field.name
      if (name != null && name.startsWith("$outer")) {
        val rv: ObjectReference = objRef.getValue(field).asInstanceOf[ObjectReference]
        if (rv != null) {
          return rv
        }
      }
    }
    null
  }

  def getModifier: Modifier = null

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    var objRef: Value = context.getThisObject
    if (iterations > 0) {
      var thisRef: ObjectReference = objRef.asInstanceOf[ObjectReference]
      var idx: Int = 0
      while (idx < iterations && thisRef != null) {
        thisRef = getOuterObject(thisRef)
        idx += 1
      }
      objRef = thisRef
    }
    if (objRef == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.this.not.avalilable"))
    }
    objRef
  }
}