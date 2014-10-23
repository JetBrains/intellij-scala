package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.jdi.{LocalVariableProxyImpl, StackFrameProxyImpl}
import com.sun.jdi.{AbsentInformationException, ObjectReference, Value}
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

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
    lazy val frameProxy: StackFrameProxyImpl = context.getFrameProxy
    var objRef: Value = context.getThisObject match {
      case null => //so we possibly in trait $class
        try {
          val variable: LocalVariableProxyImpl = frameProxy.visibleVariableByName("$this")
          if (variable == null) null
          else {
            frameProxy.getValue(variable)
          }
        } catch {
          case e: AbsentInformationException =>
            val args = frameProxy.getArgumentValues
            if (args.size() > 0) args.get(0)
            else null
        }
      case x => x
    }
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
      throw EvaluationException(DebuggerBundle.message("evaluation.error.this.not.avalilable"))
    }
    objRef
  }
}