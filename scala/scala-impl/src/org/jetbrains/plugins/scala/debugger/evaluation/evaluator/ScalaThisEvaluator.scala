package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.{LocalVariableProxyImpl, StackFrameProxyImpl}
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

import scala.jdk.CollectionConverters._

/**
 * User: Alefas
 * Date: 12.10.11
 */
class ScalaThisEvaluator(iterations: Int = 0) extends Evaluator {
  private def getOuterObject(objRef: ObjectReference): ObjectReference = {
    if (objRef == null) {
      return null
    }
    val list = objRef.referenceType.visibleFields.asScala
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

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
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
          case _: AbsentInformationException =>
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
      if (thisRef == null) throw EvaluationException(ScalaBundle.message("outer.this.not.available"))
      objRef = thisRef
    }
    if (objRef == null) {
      isStaticInterfaceMethod(context) match {
        case Some(iType) =>
          objRef = findInOtherFrame(context, iType).orNull
        case None =>
      }
    }
    if (objRef == null) {
      throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.this.not.avalilable"))
    }
    objRef
  }

  def isStaticInterfaceMethod(context: EvaluationContextImpl): Option[InterfaceType] = {
    val proxy = context.getFrameProxy
    val location = proxy.location()
    location.declaringType() match {
      case i: InterfaceType if location.method().isStatic => Some(i)
      case _ => None
    }
  }

  def findInOtherFrame(context: EvaluationContextImpl, rt: ReferenceType): Option[Value] = {
    val threadProxy = context.getFrameProxy.threadProxy()
    threadProxy.frames().asScala.collectFirst {
      case frame if frame.thisObject() != null && DebuggerUtilsEx.isAssignableFrom(rt.name(), frame.thisObject().referenceType()) =>
        frame.thisObject()
    }
  }
}
