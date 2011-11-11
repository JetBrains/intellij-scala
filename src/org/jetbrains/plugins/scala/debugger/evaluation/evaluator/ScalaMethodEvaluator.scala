package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.engine.{JVMName, DebugProcessImpl}
import com.intellij.debugger.engine.evaluation.{EvaluationContextImpl, EvaluateExceptionUtil}
import com.intellij.debugger.engine.evaluation.expression.{DisableGC, Modifier, Evaluator}
import collection.mutable.ArrayBuffer
import com.sun.jdi._
import java.lang.String
import com.intellij.debugger.{SourcePosition, DebuggerBundle}
import com.intellij.openapi.application.ApplicationManager

/**
 * User: Alefas
 * Date: 12.10.11
 */
case class ScalaMethodEvaluator(objectEvaluator: Evaluator, methodName: String, signature: JVMName,
                           argumentEvaluators: Seq[Evaluator], localMethod: Boolean,
                           traitImplementation: Option[JVMName], methodPosition: Set[SourcePosition]) extends Evaluator {
  def getModifier: Modifier = null

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    if (!context.getDebugProcess.isAttached) return null
    val debugProcess: DebugProcessImpl = context.getDebugProcess
    val requiresSuperObject: Boolean = objectEvaluator.isInstanceOf[ScSuperEvaluator] ||
      (objectEvaluator.isInstanceOf[DisableGC] &&
        (objectEvaluator.asInstanceOf[DisableGC]).getDelegate.isInstanceOf[ScSuperEvaluator])
    val obj : AnyRef = objectEvaluator.evaluate(context)
    if (obj == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException)
    }
    if (!(obj.isInstanceOf[ObjectReference] || obj.isInstanceOf[ClassType])) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.evaluating.method", methodName))
    }
    val args = argumentEvaluators.map(_.evaluate(context))
    try {
      val referenceType: ReferenceType =
      obj match {
        case o: ObjectReference =>
          val qualifierType = o.referenceType()
          debugProcess.findClass(context, qualifierType.name, qualifierType.classLoader)
        case obj: ClassType =>
          debugProcess.findClass(context, obj.name, context.getClassLoader)
        case _ =>
          null
      }
      val sign: String = if (signature != null) signature.getName(debugProcess) else null
      var mName: String = DebuggerUtilsEx.methodName(referenceType.name, methodName, sign)
      def findMethod(referenceType: ReferenceType): Method = {
        var jdiMethod: Method = null
        if (signature != null) {
          if (!localMethod) {
            jdiMethod = (referenceType.asInstanceOf[ClassType]).concreteMethodByName(methodName,
              signature.getName(debugProcess))
          }
          if (jdiMethod == null && localMethod) {
            //try to find method$i
            val methods = referenceType.allMethods()
            import scala.collection.JavaConversions._
            for (method <- methods if jdiMethod == null) {
              if (method.name().startsWith(methodName + "$")) {
                mName = DebuggerUtilsEx.methodName(referenceType.name, method.name(), sign)
                jdiMethod = (referenceType.asInstanceOf[ClassType]).concreteMethodByName(mName, signature.getName(debugProcess))
              }
            }
          }
        }
        if (jdiMethod == null) {
          val methods = referenceType.allMethods()
          import scala.collection.JavaConversions._
          val results = new ArrayBuffer[Method]
          for (method <- methods) {
            if (method.name() == methodName || (localMethod && method.name().startsWith(methodName + "$"))) {
              results += method
            }
          }

          if (results.length > 1) {
            val filtered = results.filter {
              m =>
                try {
                  if (m.isVarArgs) args.length >= m.arguments().size()
                  else args.length == m.arguments().size()
                } catch {
                  case a: AbsentInformationException => true
                }
            }
            if (filtered.length == 0) jdiMethod = results(0)
            else if (filtered.length == 1) jdiMethod = filtered(0)
            else {
              val newFiltered = filtered.filter(m => {
                var result = true
                ApplicationManager.getApplication.runReadAction(new Runnable {
                  def run() {
                    try {
                      val lines = methodPosition.map(_.getLine)
                      result = m.allLineLocations().find(l => lines.contains(l.lineNumber())) != None
                    }
                    catch {
                      case e: Exception => //ignore
                    }
                  }
                })
                result
              })
              if (newFiltered.isEmpty)
                jdiMethod = filtered(0)
              else jdiMethod = newFiltered(0)
            }
          } else if (results.length == 1) jdiMethod = results(0)
        }
        jdiMethod
      }
      if (obj.isInstanceOf[ClassType]) {
        if (referenceType.isInstanceOf[ClassType]) {
          val jdiMethod = findMethod(referenceType)
          if (jdiMethod != null && jdiMethod.isStatic) {
            import scala.collection.JavaConversions._
            return debugProcess.invokeMethod(context, referenceType.asInstanceOf[ClassType], jdiMethod, args)
          }
        }
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.static.method", mName))
      }
      val objRef: ObjectReference = obj.asInstanceOf[ObjectReference]
      var _refType: ReferenceType = referenceType
      if (requiresSuperObject && referenceType.isInstanceOf[ClassType]) {
        traitImplementation match {
          case Some(tr) =>
            val className: String = tr.getName(context.getDebugProcess)
            if (className != null) {
              context.getDebugProcess.findClass(context, className, context.getClassLoader) match {
                case c: ClassType => _refType = c
                case _ => _refType = (referenceType.asInstanceOf[ClassType]).superclass
              }
            } else _refType = (referenceType.asInstanceOf[ClassType]).superclass
          case _ =>
            _refType = (referenceType.asInstanceOf[ClassType]).superclass
        }
      }
      val jdiMethod: Method = findMethod(_refType)
      if (jdiMethod == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.instance.method", mName))
      }
      import scala.collection.JavaConversions._
      if (requiresSuperObject) {
        traitImplementation match {
          case Some(tr) =>
            val className: String = tr.getName(context.getDebugProcess)
            if (className != null) {
              context.getDebugProcess.findClass(context, className, context.getClassLoader) match {
                case c: ClassType =>
                  return debugProcess.invokeMethod(context, c, jdiMethod, obj +: args)
                case _ =>
              }
            }
          case None =>
        }
        return debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, args, ObjectReference.INVOKE_NONVIRTUAL)
      }
      debugProcess.invokeMethod(context, objRef, jdiMethod, args)
    }
    catch {
      case e: Exception => {
        throw EvaluateExceptionUtil.createEvaluateException(e)
      }
    }
  }
}