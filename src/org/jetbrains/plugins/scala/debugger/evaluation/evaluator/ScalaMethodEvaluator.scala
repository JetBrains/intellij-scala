package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{DisableGC, Evaluator, Modifier}
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, JVMName}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.{DebuggerBundle, SourcePosition}
import com.intellij.openapi.application.ApplicationManager
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import scala.collection.mutable

/**
 * User: Alefas
 * Date: 12.10.11
 */
case class ScalaMethodEvaluator(objectEvaluator: Evaluator, _methodName: String, signature: JVMName,
                           argumentEvaluators: Seq[Evaluator], traitImplementation: Option[JVMName] = None,
                           methodPosition: Set[SourcePosition] = Set.empty, localMethodIndex: Int = -1) extends Evaluator {
  def getModifier: Modifier = null

  val methodName = DebuggerUtil.withoutBackticks(_methodName)
  private val localMethod = localMethodIndex > 0
  private val localMethodName = methodName + "$" + localMethodIndex

  private var prevProcess: DebugProcess = null
  private val jdiMethodsCache = mutable.HashMap[ReferenceType, Option[Method]]()

  private def initCache(process: DebugProcess): Unit = {
    if (process != null) {
      prevProcess = process
      jdiMethodsCache.clear()
    }
  }

  private def getOrUpdateMethod(referenceType: ReferenceType, findMethod: ReferenceType => Method): Option[Method] = {
    jdiMethodsCache.getOrElseUpdate(referenceType, Option(findMethod(referenceType)))
  }

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    val debugProcess: DebugProcessImpl = context.getDebugProcess
    if (!debugProcess.isAttached) return null
    if (debugProcess != prevProcess) {
      initCache(debugProcess)
    }
    val requiresSuperObject: Boolean = objectEvaluator.isInstanceOf[ScSuperEvaluator] ||
      (objectEvaluator.isInstanceOf[DisableGC] &&
        objectEvaluator.asInstanceOf[DisableGC].getDelegate.isInstanceOf[ScSuperEvaluator])
    val obj : AnyRef = DebuggerUtil.unwrapScalaRuntimeObjectRef {
      objectEvaluator.evaluate(context)
    }
    if (obj == null) {
      throw EvaluationException(new NullPointerException)
    }
    if (!(obj.isInstanceOf[ObjectReference] || obj.isInstanceOf[ClassType])) {
      throw EvaluationException(DebuggerBundle.message("evaluation.error.evaluating.method", methodName))
    }
    val args = argumentEvaluators.flatMap { ev =>
      val result = ev.evaluate(context)
      if (result == FromLocalArgEvaluator.skipMarker) None
      else Some(result)
    }
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
      val sign: String = if (signature != null && args.size == argumentEvaluators.size) signature.getName(debugProcess) else null
      var mName: String = DebuggerUtilsEx.methodName(referenceType.name, methodName, sign)
      def findMethod(referenceType: ReferenceType): Method = {
        import scala.collection.JavaConversions._
        def sortedMethodCandidates: List[Method] = {
          val allMethods = referenceType.allMethods()
          allMethods.toList.collect {
            case method if !localMethod && method.name() == methodName => (method, 1)
            case method if !localMethod && method.name().endsWith("$$" + methodName) => (method, 1) //private method, maybe from parent class
            case method if localMethod && method.name() == localMethodName => (method, 1)
            case method if localMethod && method.name.startsWith(methodName + "$") => (method, 2)
            case method if localMethod && method.name.contains(methodName + "$") => (method, 3)
          }.sortBy(_._2).map(_._1)
        }
        var jdiMethod: Method = null
        if (signature != null) {
          if (!localMethod) {
            jdiMethod = referenceType.asInstanceOf[ClassType].concreteMethodByName(methodName,
              signature.getName(debugProcess))
          }
          if (jdiMethod == null && localMethod) {
            for (method <- sortedMethodCandidates if jdiMethod == null) {
              mName = DebuggerUtilsEx.methodName(referenceType.name, method.name(), sign)
              jdiMethod = referenceType.asInstanceOf[ClassType].concreteMethodByName(mName, signature.getName(debugProcess))
              if (jdiMethod != null) return jdiMethod
            }
          }
        }
        if (jdiMethod == null) {
          if (sortedMethodCandidates.length > 1) {
            val filtered = sortedMethodCandidates.filter {
              m =>
                try {
                  if (m.isVarArgs) args.length >= m.argumentTypeNames().size()
                  else args.length == m.argumentTypeNames().size()
                } catch {
                  case a: AbsentInformationException => true
                }
            }
            if (filtered.isEmpty) jdiMethod = sortedMethodCandidates.head
            else if (filtered.length == 1) jdiMethod = filtered.head
            else {
              val newFiltered = filtered.filter(m => {
                var result = true
                ApplicationManager.getApplication.runReadAction(new Runnable {
                  def run() {
                    try {
                      val lines = methodPosition.map(_.getLine)
                      result = m.allLineLocations().exists(l => lines.contains(ScalaPositionManager.checkedLineNumber(l)))
                    }
                    catch {
                      case e: Exception => //ignore
                    }
                  }
                })
                result
              })
              if (newFiltered.isEmpty)
                jdiMethod = filtered.head
              else jdiMethod = newFiltered.head
            }
          } else if (sortedMethodCandidates.length == 1) jdiMethod = sortedMethodCandidates.head
        }
        jdiMethod
      }
      if (obj.isInstanceOf[ClassType]) {
        referenceType match {
          case classType: ClassType =>
            val jdiMethod = getOrUpdateMethod(referenceType, findMethod).orNull
            if (jdiMethod != null && methodName == "<init>") {
              import scala.collection.JavaConversions._
              return debugProcess.newInstance(context, classType, jdiMethod, unwrappedArgs(args, jdiMethod))
            }
            if (jdiMethod != null && jdiMethod.isStatic) {
              import scala.collection.JavaConversions._
              return debugProcess.invokeMethod(context, classType, jdiMethod, unwrappedArgs(args, jdiMethod))
            }
          case _ =>
        }
        throw EvaluationException(DebuggerBundle.message("evaluation.error.no.static.method", mName))
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
                case _ => _refType = referenceType.asInstanceOf[ClassType].superclass
              }
            } else _refType = referenceType.asInstanceOf[ClassType].superclass
          case _ =>
            _refType = referenceType.asInstanceOf[ClassType].superclass
        }
      }
      val jdiMethod: Method = getOrUpdateMethod(_refType, findMethod).orNull
      if (jdiMethod == null) {
        throw EvaluationException(DebuggerBundle.message("evaluation.error.no.instance.method", mName))
      }
      import scala.collection.JavaConversions._
      if (requiresSuperObject) {
        traitImplementation match {
          case Some(tr) =>
            val className: String = tr.getName(context.getDebugProcess)
            if (className != null) {
              context.getDebugProcess.findClass(context, className, context.getClassLoader) match {
                case c: ClassType =>
                  return debugProcess.invokeMethod(context, c, jdiMethod, unwrappedArgs(obj +: args, jdiMethod))
                case _ =>
              }
            }
          case None =>
        }
        return debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, args, ObjectReference.INVOKE_NONVIRTUAL)
      }
      debugProcess.invokeMethod(context, objRef, jdiMethod, unwrappedArgs(args, jdiMethod))
    }
    catch {
      case e: Exception => throw EvaluationException(e)
    }
  }

  private def unwrappedArgs(args: Seq[AnyRef], jdiMethod: Method): Seq[AnyRef] = {
    val argTypeNames = jdiMethod.argumentTypeNames()
    args.zipWithIndex.map {
      case (DebuggerUtil.scalaRuntimeRefTo(value), idx) if !DebuggerUtil.isScalaRuntimeRef(argTypeNames.get(idx)) => value
      case (arg, _) => arg
    }
  }
}