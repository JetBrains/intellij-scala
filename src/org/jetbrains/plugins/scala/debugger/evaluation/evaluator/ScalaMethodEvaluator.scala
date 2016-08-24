package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{DisableGC, Evaluator, Modifier}
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, JVMName}
import com.intellij.debugger.{DebuggerBundle, SourcePosition}
import com.sun.jdi._
import com.sun.tools.jdi.ConcreteMethodImpl
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.inReadAction

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.{Success, Try}

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
    val args: Seq[Value] = argumentEvaluators.flatMap { ev =>
      ev.evaluate(context) match {
        case Some(res) => Some(res.asInstanceOf[Value])
        case None => None
        case res => Some(res.asInstanceOf[Value])
      }
    }
    try {
      def findClass(name: String) = debugProcess.findClass(context, name, context.getClassLoader)

      def findMethod(referenceType: ReferenceType): Option[Method] = {
        lazy val sortedMethodCandidates: List[Method] = {
          val allMethods = referenceType.allMethods()
          allMethods.toList.collect {
            case method if !localMethod && method.name() == methodName => (method, 1)
            case method if !localMethod && method.name().endsWith("$$" + methodName) => (method, 1) //private method, maybe from parent class
            case method if localMethod && method.name() == localMethodName => (method, 1)
            case method if localMethod && method.name.startsWith(methodName + "$") => (method, 2)
            case method if localMethod && method.name.contains(methodName + "$") => (method, 3)
          }.sortBy(_._2).map(_._1)
        }
        def concreteMethodByName(mName: String, signature: JVMName): Option[Method] = {
          val sgn = signature.getName(debugProcess)
          referenceType match {
            case classType: ClassType =>
              Option(classType.concreteMethodByName(mName, sgn))
            case it: InterfaceType =>
              it.methodsByName(mName, sgn).find(_.isInstanceOf[ConcreteMethodImpl])
          }
        }
        def findWithSignature(): Option[Method] = {
          if (signature == null) None
          else {
            if (!localMethod) concreteMethodByName(methodName, signature)
            else {
              sortedMethodCandidates.toStream
                .flatMap(m => concreteMethodByName(m.name(), signature))
                .headOption
            }
          }
        }
        def findWithoutSignature(): Option[Method] = {
          def sameParamNumber(m: Method) = {
            try {
              if (m.isVarArgs) args.length >= m.argumentTypeNames().size()
              else args.length == m.argumentTypeNames().size()
            }
            catch {
              case a: AbsentInformationException => true
            }
          }
          def linesIntersects(m: Method): Boolean = inReadAction {
            Try {
              val lines = methodPosition.map(_.getLine)
              m.allLineLocations().exists(l => lines.contains(ScalaPositionManager.checkedLineNumber(l)))
            }.getOrElse(true)
          }

          if (sortedMethodCandidates.length > 1) {
            val withSameParamNumber = sortedMethodCandidates.filter(sameParamNumber)
            if (withSameParamNumber.isEmpty) sortedMethodCandidates.headOption
            else if (withSameParamNumber.length == 1) withSameParamNumber.headOption
            else {
              val withSameLines = withSameParamNumber.filter(linesIntersects)
              withSameLines.headOption.orElse(withSameParamNumber.headOption)
            }
          }
          else sortedMethodCandidates.headOption
        }

        def doFind() = findWithSignature() orElse findWithoutSignature()

        jdiMethodsCache.getOrElseUpdate(referenceType, doFind())
      }

      def invokeStaticMethod(referenceType: ReferenceType): AnyRef = {
        def noStaticMethodException = EvaluationException(DebuggerBundle.message("evaluation.error.no.static.method", methodName))

        referenceType match {
          case classType: ClassType =>
            val jdiMethod = findMethod(classType).orNull
            if (jdiMethod != null && methodName == "<init>") {
              debugProcess.newInstance(context, classType, jdiMethod, unwrappedArgs(args, jdiMethod))
            }
            else if (jdiMethod != null && jdiMethod.isStatic) {
              debugProcess.invokeMethod(context, classType, jdiMethod, unwrappedArgs(args, jdiMethod))
            }
            else throw noStaticMethodException
          case _ =>
            throw noStaticMethodException
        }
      }

      def findAndInvokeInstanceMethod(objRef: ObjectReference): AnyRef = {
        val objType = findClass(objRef.referenceType().name())
        if (objType.isInstanceOf[ArrayType]) throw EvaluationException(s"Method $methodName cannot be invoked on array")

        val classType = objType.asInstanceOf[ClassType]

        def classWithMethod(c: ReferenceType) = findMethod(c).map(m => (c, m))

        val classAndMethod = if (requiresSuperObject) {
          val superClass = classType.superclass()
          classWithMethod(superClass)
            .orElse {
              traitImplementation.flatMap(ti => Option(ti.getName(context.getDebugProcess))) match {
                case Some(traitImplName) =>
                  Try(findClass(traitImplName)) match {
                    case Success(c: ClassType) => classWithMethod(c)
                    case _ =>
                      val traitName = traitImplName.stripSuffix("$class")
                      Try(findClass(traitName)).toOption.flatMap(classWithMethod)
                  }
                case _ => None
              }
            }
        }
        else classWithMethod(classType)

        classAndMethod match {
          case Some((clazz: ClassType, method)) if clazz.name.endsWith("$class") =>
            debugProcess.invokeMethod(context, clazz, method, unwrappedArgs(obj +: args, method))
          case Some((_, method)) if requiresSuperObject =>
            debugProcess.invokeInstanceMethod(context, objRef, method, args, ObjectReference.INVOKE_NONVIRTUAL)
          case Some((clazz, method)) =>
            debugProcess.invokeMethod(context, objRef, method, unwrappedArgs(args, method))
          case None =>
            throw EvaluationException(DebuggerBundle.message("evaluation.error.evaluating.method", methodName))
        }
      }

      obj match {
        case objRef: ObjectReference =>
          findAndInvokeInstanceMethod(objRef)
        case obj: ClassType =>
          val referenceType = findClass(obj.name)
          invokeStaticMethod(referenceType)
        case _ =>
          throw EvaluationException(DebuggerBundle.message("evaluation.error.evaluating.method", methodName))
      }
    }
    catch {
      case e: Exception => throw EvaluationException(e)
    }
  }

  private def unwrappedArgs(args: Seq[AnyRef], jdiMethod: Method): Seq[Value] = {
    val argTypeNames = jdiMethod.argumentTypeNames()
    args.zipWithIndex.map {
      case (DebuggerUtil.scalaRuntimeRefTo(value), idx) if !DebuggerUtil.isScalaRuntimeRef(argTypeNames.get(idx)) => value.asInstanceOf[Value]
      case (arg, _) => arg.asInstanceOf[Value]
    }
  }
}