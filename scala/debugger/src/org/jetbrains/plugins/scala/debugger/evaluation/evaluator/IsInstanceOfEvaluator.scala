package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.JVMName
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, TypeEvaluator}
import com.intellij.util.concurrency.annotations.{RequiresReadLock, RequiresReadLockAbsence}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScLiteralType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.debugger.DebuggerBundle

import scala.jdk.CollectionConverters._

class IsInstanceOfEvaluator @RequiresReadLock (operandEvaluator: Evaluator, rawType: ScType) extends Evaluator {

  // Everything derived from the type is computed here, under the read action the evaluator is built in;
  // evaluate() only talks to the debuggee
  private val tpe: ScType = rawType.removeAliasDefinitions()
  private val typeText: String = tpe.toString
  private val literalEvaluator: Option[Evaluator] = tpe match {
    case lt: ScLiteralType => Some(new ScalaLiteralEvaluator(lt.value.value.asInstanceOf[AnyRef], lt.wideType))
    case _ => None
  }
  private val jvmName: JVMName = DebuggerUtil.getJVMQualifiedName(tpe)

  //noinspection ApiStatus,UnstableApiUsage
  @RequiresReadLockAbsence
  override def evaluate(context: EvaluationContextImpl): BooleanValue = {

    def booleanValue(b: Boolean): BooleanValue =
      context.getDebugProcess.getVirtualMachineProxy.mirrorOf(b)

    def primitiveClassOfValue(value: PrimitiveValue): Class[?] = value match {
      case _: BooleanValue => classOf[Boolean]
      case _: ByteValue => classOf[Byte]
      case _: CharValue => classOf[Char]
      case _: DoubleValue => classOf[Double]
      case _: FloatValue => classOf[Float]
      case _: IntegerValue => classOf[Int]
      case _: LongValue => classOf[Long]
      case _: ShortValue => classOf[Short]
    }

    def throwCannotTestReferenceException(valueType: String): Nothing =
      throw EvaluationException(
        DebuggerBundle.message("error.value.isinstanceof.reference", valueType, typeText))

    def throwTypeCannotBeUsedInIsInstanceOfException(kind: String): Nothing =
      throw EvaluationException(DebuggerBundle.message("error.type.cannot.be.used.in.isinstanceof", kind, typeText))

    object Primitive {
      def unapply(tpe: ScType): Option[Class[?]] = {
        val stdTypes = tpe.projectContext.stdTypes
        import stdTypes._

        tpe match {
          case Boolean => Some(classOf[Boolean])
          case Byte => Some(classOf[Byte])
          case Char => Some(classOf[Char])
          case Double => Some(classOf[Double])
          case Float => Some(classOf[Float])
          case Int => Some(classOf[Int])
          case Long => Some(classOf[Long])
          case Short => Some(classOf[Short])
          case _ => None
        }
      }
    }

    object Literal {
      def unapply(tpe: ScType): Option[Value] = literalEvaluator.map(_.evaluate(context).asInstanceOf[Value])
    }

    def evaluateEqualsOnPrimitiveValues(x: PrimitiveValue, y: PrimitiveValue): Boolean =
      (x, y) match {
        case (x: BooleanValue, y: BooleanValue) => x.value() == y.value()
        case (x: ByteValue, y: ByteValue) => x.value() == y.value()
        case (x: CharValue, y: CharValue) => x.value() == y.value()
        case (x: DoubleValue, y: DoubleValue) => x.value() == y.value()
        case (x: FloatValue, y: FloatValue) => x.value() == y.value()
        case (x: IntegerValue, y: IntegerValue) => x.value() == y.value()
        case (x: LongValue, y: LongValue) => x.value() == y.value()
        case (x: ShortValue, y: ShortValue) => x.value() == y.value()
        case _ => false
      }

    val lhs = operandEvaluator.evaluate(context).asInstanceOf[Value]
    val stdTypes = tpe.projectContext.stdTypes
    import stdTypes._

    (lhs, tpe) match {
      case (_, Any) =>
        booleanValue(true)

      case (_, AnyVal | Null | Nothing | Singleton) =>
        // scalac compiler error "type AnyVal cannot be used in a type patter or isInstanceOf test"
        val kind = if (tpe == Singleton) "trait" else "class"
        throwTypeCannotBeUsedInIsInstanceOfException(kind)

      case (_, _: ScCompoundType) =>
        throw EvaluationException(DebuggerBundle.message("error.isinstanceof.structural.type"))

      case (null, _) =>
        // case: null.isInstanceOf[String]
        booleanValue(false)

      case (lhs: PrimitiveValue, Primitive(rhsClass)) =>
        // case: 123.isInstanceOf[Int]
        val lhsClass = primitiveClassOfValue(lhs)
        val result = rhsClass.isAssignableFrom(lhsClass)
        booleanValue(result)

      case (lhs: PrimitiveValue, Literal(rhs: PrimitiveValue)) =>
        // case: 123.isInstanceOf[123]
        val result = evaluateEqualsOnPrimitiveValues(lhs, rhs)
        booleanValue(result)

      case (lhs: PrimitiveValue, _) =>
        // case: 123.isInstanceOf["abc"]
        // case: 123.isInstanceOf[String]
        // scalac compiler error "isInstanceOf cannot test if value types are references"
        val valueType = lhs match {
          case _: BooleanValue => "Boolean"
          case _: ByteValue => "Byte"
          case _: CharValue => "Char"
          case _: DoubleValue => "Double"
          case _: FloatValue => "Float"
          case _: IntegerValue => "Int"
          case _: LongValue => "Long"
          case _: ShortValue => "Short"
        }
        throwCannotTestReferenceException(valueType)

      case (_: ObjectReference, Primitive(_)) =>
        // case: "abc".isInstanceOf[Int]
        booleanValue(false)

      case (_: ObjectReference, Literal(_: PrimitiveValue)) =>
        // case: "abc".isInstanceOf[123]
        booleanValue(false)

      case (lhs: ObjectReference, Literal(rhs: ObjectReference)) =>
        // case: "abc".isInstanceOf["abc"]
        try {
          val classType = lhs.referenceType().asInstanceOf[ClassType]
          val method = classType.concreteMethodByName("equals", "(Ljava/lang/Object;)Z")
          val args = List(rhs)
          val result = context.getDebugProcess.invokeMethod(context, lhs, method, args.asJava).asInstanceOf[BooleanValue].value()
          booleanValue(result)
        } catch {
          case e: Exception =>
            throw EvaluationException(e)
        }

      case (lhs: ObjectReference, _) =>
        // case: "abc".isInstanceOf[String]
        try {
          val typeEvaluator = new TypeEvaluator(jvmName)
          val refType = typeEvaluator.evaluate(context)
          val classObject = refType.classObject()
          val classRefType = classObject.referenceType().asInstanceOf[ClassType]
          val method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z")
          val args = List(lhs.referenceType().classObject())
          context.getDebugProcess.invokeMethod(context, classObject, method, args.asJava).asInstanceOf[BooleanValue]
        } catch {
          case e: Exception =>
            throw EvaluationException(e)
        }
      case x =>
        throw new MatchError(x)
    }
  }
}
