package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.JVMName
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, TypeEvaluator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

import scala.jdk.CollectionConverters._

class ScalaInstanceofEvaluator(operandEvaluator: Evaluator, optTpe: Option[ScType]) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): AnyRef = {

    def booleanValue(b: Boolean): BooleanValue =
      context.getDebugProcess.getVirtualMachineProxy.mirrorOf(b)

    def primitiveClassOfValue(value: PrimitiveValue): Class[_] = value match {
      case _: BooleanValue => classOf[Boolean]
      case _: ByteValue => classOf[Byte]
      case _: CharValue => classOf[Char]
      case _: DoubleValue => classOf[Double]
      case _: FloatValue => classOf[Float]
      case _: IntegerValue => classOf[Int]
      case _: LongValue => classOf[Long]
      case _: ShortValue => classOf[Short]
    }

    def throwCannotTestReferenceException() =
      throw EvaluationException(
        ScalaBundle.message("error.value.isinstanceof.reference"))

    def throwTypeCannotBeUsedInIsInstanceOfException(tpe: ScType) =
      throw EvaluationException(ScalaBundle.message("error.type.cannot.be.used.in.isinstanceof", tpe))

    object Primitive {
      def unapply(tpe: ScType): Option[Class[_]] = {
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
      def unapply(tpe: ScType): Option[Value] = tpe match {
        case lt: ScLiteralType =>
          val lv = lt.value.value.asInstanceOf[AnyRef]
          val wt = ApplicationManager.getApplication.runReadAction(new Computable[ScType] {
            override def compute(): ScType = lt.wideType
          })
          Some(new ScalaLiteralEvaluator(lv, wt).evaluate(context).asInstanceOf[Value])
        case _ => None
      }
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

    val value = operandEvaluator.evaluate(context).asInstanceOf[Value]
    val dealiased = optTpe.map(_.removeAliasDefinitions())
    
    value match {
      case pv: PrimitiveValue =>
        dealiased match {
          case None =>
            // case: 123.isInstanceOf
            // scalac compiler error "isInstanceOf cannot test if value types are references"
            throwCannotTestReferenceException()

          case Some(tpe) =>
            val stdTypes = tpe.projectContext.stdTypes
            import stdTypes._

            tpe.removeAliasDefinitions() match {
              case AnyVal | Null | Nothing =>
                // case: 123.isInstanceOf[AnyVal]
                // scalac compiler error "type AnyVal cannot be used in a type pattern or isInstanceOf test"
                throwTypeCannotBeUsedInIsInstanceOfException(tpe)

              case Primitive(cls) =>
                // case: 123.isInstanceOf[Int]
                val primCls = primitiveClassOfValue(pv)
                val result = cls.isAssignableFrom(primCls)
                booleanValue(result)

              case Literal(literal) =>
                literal match {
                  case prim: PrimitiveValue =>
                    // case: 123.isInstanceOf[123]
                    booleanValue(evaluateEqualsOnPrimitiveValues(pv, prim))
                  case _: ObjectReference =>
                    // case: 123.isInstanceOf["123"]
                    // scalac compiler error "isInstanceOf cannot test if value types are references"
                    throwCannotTestReferenceException()
                }

              case _ =>
                // case: 123.isInstanceOf[String]
                // scalac compiler error "isInstanceOf cannot test if value types are references"
                throwCannotTestReferenceException()
            }
        }

      case ref: ObjectReference =>
        dealiased match {
          case None =>
            // case: "abc".isInstanceOf
            booleanValue(false)

          case Some(tpe) =>
            val stdTypes = tpe.projectContext.stdTypes
            import stdTypes._

            tpe.removeAliasDefinitions() match {
              case AnyVal | Null | Nothing =>
                // case: "abc".isInstanceOf[AnyVal]
                // scalac compiler error "type AnyVal cannot be used in a type pattern or isInstanceOf test"
                throwTypeCannotBeUsedInIsInstanceOfException(tpe)

              case Primitive(_) =>
                // case: "abc".isInstanceOf[Int]
                booleanValue(false)

              case Literal(literal) =>
                val result = literal match {
                  case _: PrimitiveValue =>
                    // case: "abc".isInstanceOf[123]
                    false
                  case _: ObjectReference =>
                    // case: "abc".isInstanceOf["abc"]
                    try {
                      val classType = ref.referenceType().asInstanceOf[ClassType]
                      val method = classType.concreteMethodByName("equals", "(Ljava/lang/Object;)Z")
                      val args = List(literal)
                      context.getDebugProcess.invokeMethod(context, ref, method, args.asJava).asInstanceOf[BooleanValue].value()
                    } catch {
                      case e: Exception =>
                        throw EvaluationException(e)
                    }
                }
                booleanValue(result)

              case _ =>
                // case: "abc".isInstanceOf[String]
                try {
                  // This call crashes unless wrapped in `runReadAction`
                  val name = ApplicationManager.getApplication.runReadAction(new Computable[JVMName] {
                    override def compute(): JVMName = DebuggerUtil.getJVMQualifiedName(tpe)
                  })
                  val typeEvaluator = new TypeEvaluator(name)
                  val refType = typeEvaluator.evaluate(context)
                  val classObject = refType.classObject()
                  val classRefType = classObject.referenceType().asInstanceOf[ClassType]
                  val method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z")
                  val args = List(ref.referenceType().classObject())
                  context.getDebugProcess.invokeMethod(context, classObject, method, args.asJava)
                } catch {
                  case e: Exception =>
                    throw EvaluationException(e)
                }
            }
        }

      case null =>
        dealiased match {
          case None =>
            // case: null.isInstanceOf
            booleanValue(false)

          case Some(tpe) =>
            val stdTypes = tpe.projectContext.stdTypes
            import stdTypes._

            tpe.removeAliasDefinitions() match {
              case AnyVal | Null | Nothing =>
                // case: null.isInstanceOf[AnyVal]
                // scalac compiler error "type AnyVal cannot be used in a type pattern or isInstanceOf test"
                throwTypeCannotBeUsedInIsInstanceOfException(tpe)

              case _ =>
                // case: null.isInstanceOf[String]
                booleanValue(false)
            }
        }
    }
  }
}
