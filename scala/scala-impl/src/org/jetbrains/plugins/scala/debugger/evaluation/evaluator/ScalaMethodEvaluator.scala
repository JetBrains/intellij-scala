package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{DisableGC, Evaluator}
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, DebuggerUtils, JVMName}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.{JavaDebuggerBundle, SourcePosition}
import com.sun.jdi._
import com.sun.tools.jdi.{ConcreteMethodImpl, TypeComponentImpl}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Success, Try}

case class ScalaMethodEvaluator(objectEvaluator: Evaluator,
                                _methodName: String,
                                signature: JVMName,
                                argumentEvaluators: Seq[Evaluator],
                                traitImplementation: Option[JVMName] = None,
                                methodPosition: Set[SourcePosition] = Set.empty,
                                localMethodIndex: Int = -1) extends Evaluator {

  val methodName: String = DebuggerUtil.withoutBackticks(_methodName)
  private val localMethod = localMethodIndex > 0
  private val localMethodName = methodName + "$" + localMethodIndex

  private var prevProcess: DebugProcess = _
  private val jdiMethodsCache = mutable.HashMap[ReferenceType, Option[Method]]()

  private def initCache(process: DebugProcess): Unit = {
    if (process != null) {
      prevProcess = process
      jdiMethodsCache.clear()
    }
  }

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    val debugProcess: DebugProcessImpl = context.getDebugProcess
    if (!debugProcess.isAttached) return null
    if (debugProcess != prevProcess) {
      initCache(debugProcess)
    }
    val requiresSuperObject: Boolean = objectEvaluator.isInstanceOf[ScSuperEvaluator] ||
      (objectEvaluator.is[DisableGC] &&
        DisableGC.unwrap(objectEvaluator).isInstanceOf[ScSuperEvaluator])
    val evaluated: AnyRef = {
      val res = objectEvaluator.evaluate(context)
      DebuggerUtil.unwrapScalaRuntimeRef(res)
    }
    val obj = evaluated match {
      case p: PrimitiveValue => ScalaBoxingEvaluator.box(p, context)
      case _ => evaluated
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
      def findClass(name: String): ReferenceType = debugProcess.findClass(context, name, context.getClassLoader)

      def findMethod(referenceType: ReferenceType): Option[Method] = {
        lazy val sortedMethodCandidates: List[Method] = {
          val allMethods = referenceType.allMethods()
          allMethods.asScala.collect {
            case method if !localMethod && method.name() == methodName => (method, 1)
            case method if !localMethod && method.name().endsWith("$$" + methodName) => (method, 1) //private method, maybe from parent class
            case method if localMethod && method.name() == localMethodName => (method, 1)
            case method if localMethod && method.name.startsWith(methodName + "$") => (method, 2)
            case method if localMethod && method.name.contains(methodName + "$") => (method, 3)
          }
            .sortBy(_._2)
            .map(_._1)
            .toList
        }
        def concreteMethodByName(mName: String, signature: JVMName): Option[Method] = {
          val sgn = signature.getName(debugProcess)
          referenceType match {
            case classType: ClassType =>
              Option(classType.concreteMethodByName(mName, sgn))
            case it: InterfaceType =>
              it.methodsByName(mName, sgn).asScala.find(_.is[ConcreteMethodImpl])
          }
        }
        def findWithSignature(): Option[Method] = {
          if (signature == null) None
          else {
            if (!localMethod) concreteMethodByName(methodName, signature)
            else {
              sortedMethodCandidates.to(LazyList)
                .flatMap(m => concreteMethodByName(m.name(), signature))
                .headOption
            }
          }
        }
        def findWithoutSignature(): Option[Method] = {
          def sameParamNumber(m: Method) = {
            try {
              val argsCount = m.argumentTypeNames().size()
              if (m.isVarArgs) args.length >= argsCount
              else args.length == argsCount || args.length == argsCount - 1
            }
            catch {
              case _: AbsentInformationException => true
            }
          }
          def linesIntersects(m: Method): Boolean = inReadAction {
            Try {
              val lines = methodPosition.map(_.getLine)
              m.allLineLocations().asScala.exists(l => lines.contains(ScalaPositionManager.checkedLineNumber(l)))
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

      def invokeStaticMethod(referenceType: ReferenceType, jdiMethod: Method): AnyRef = {
        def fixArguments(): Seq[Value] = {
          def correctArgType(arg: AnyRef, typeName: String) = arg match {
            case objRef: ObjectReference => DebuggerUtilsEx.isAssignableFrom(typeName, objRef.referenceType())
            case primValue: PrimitiveValue => primValue.`type`().name() == typeName
            case _ => true
          }
          val paramTypeNames = jdiMethod.argumentTypeNames()
          if (paramTypeNames.size() == 0) Seq.empty
          else {
            val needObj = args.isEmpty || !correctArgType(args.head, paramTypeNames.get(0))
            if (needObj) unwrappedArgs(obj +: args, jdiMethod)
            else unwrappedArgs(args, jdiMethod)
          }
        }

        referenceType match {
          case ct: ClassType =>
            debugProcess.invokeMethod(context, ct, jdiMethod, fixArguments().asJava)
          case it: InterfaceType =>
            debugProcess.invokeMethod(context, it, jdiMethod, fixArguments().asJava)
        }
      }

      def invokeConstructor(referenceType: ReferenceType, method: Method): AnyRef = {
        referenceType match {
          case ct: ClassType if methodName == "<init>" =>
            debugProcess.newInstance(context, ct, method, unwrappedArgs(args, method).asJava)
          case _ => throw EvaluationException(ScalaBundle.message("could.not.find.appropriate.constructor.for.name", referenceType.name()))
        }
      }

      def invokeInstanceMethod(objRef: ObjectReference, jdiMethod: Method): AnyRef = {
        if (requiresSuperObject)
          debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, unwrappedArgs(args, jdiMethod).asJava, ObjectReference.INVOKE_NONVIRTUAL)
        else
          debugProcess.invokeMethod(context, objRef, jdiMethod, unwrappedArgs(args, jdiMethod).asJava)
      }

      def invokeInterfaceMethod(objRef: ObjectReference, jdiMethod: Method): AnyRef = {
        def togglePrivate(method: Method): Unit = {
          try {
            method match {
              case mImpl: TypeComponentImpl =>
                val field = classOf[TypeComponentImpl].getDeclaredField("modifiers")
                field.setAccessible(true)
                val value = field.get(mImpl).asInstanceOf[Integer].toInt
                val privateModifierMask = 2
                field.set(mImpl, value ^ privateModifierMask)
              case _ =>
            }
          } catch {
            case _: Throwable =>
          }
        }

        if (jdiMethod.isAbstract) throw EvaluationException(ScalaBundle.message("cannot.invoke.abstract.interface.method.name", jdiMethod.name()))

        //see SCL-10132
        if (!jdiMethod.isDefault && jdiMethod.isPrivate) {
          togglePrivate(jdiMethod)
          val result = debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, unwrappedArgs(args, jdiMethod).asJava, ObjectReference.INVOKE_NONVIRTUAL)
          togglePrivate(jdiMethod)
          result
        } else {
          debugProcess.invokeMethod(context, objRef, jdiMethod, unwrappedArgs(args, jdiMethod).asJava)
        }
      }

      def classWithMethod(c: ReferenceType) = findMethod(c).map(m => (c, m))

      def findInSuperClass(classType: ClassType): Option[(ReferenceType, Method)] = {
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

      val typeAndMethod: Option[(ReferenceType, Method)] = obj match {
        case objRef: ObjectReference =>
          val objType = objRef.referenceType()
          if (objType.is[ArrayType]) {
            Option(signature).map(_.getName(debugProcess)).flatMap { sgn =>
              Option(DebuggerUtils.findMethod(objType, methodName, sgn))
            }.fold {
              throw EvaluationException(ScalaBundle.message("method.methodname.cannot.be.invoked.on.array", methodName))
            }(Some(objType, _))
          } else {
            val classType = objType.asInstanceOf[ClassType]
            if (requiresSuperObject) findInSuperClass(classType)
            else classWithMethod(classType)
          }
        case rt: ReferenceType =>
          classWithMethod(rt)
        case _ =>
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.method", methodName))
      }

      if (typeAndMethod.isEmpty) throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.method", methodName))

      typeAndMethod match {
        case Some((tp, m)) if m.isConstructor =>
          invokeConstructor(tp, m)
        case Some((tp, m)) if m.isStatic =>
          invokeStaticMethod(tp, m)
        case Some((_, m)) =>
          obj match {
            case objRef: ObjectReference if m.declaringType().is[InterfaceType] =>
              invokeInterfaceMethod(objRef, m)
            case objRef: ObjectReference =>
              invokeInstanceMethod(objRef, m)
            case _ =>
              throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.method", methodName))
          }
        case _ => throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.method", methodName))
      }
    }
    catch {
      case e: Exception => throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.method", methodName), e)
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
