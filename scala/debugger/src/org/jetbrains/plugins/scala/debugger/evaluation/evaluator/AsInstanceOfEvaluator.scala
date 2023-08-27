package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}
import org.jetbrains.plugins.scala.project.ProjectExt

class AsInstanceOfEvaluator(operandEvaluator: Evaluator, rawType: ScType) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    val proxy = context.getDebugProcess.getVirtualMachineProxy

    object NumericType {
      def unapply(tpe: ScType): Option[PrimitiveValue => Value] = {
        val stdTypes = tpe.getProject.stdTypes
        import stdTypes._
        tpe match {
          case Byte => Some(pv => proxy.mirrorOf(pv.byteValue))
          case Char => Some(pv => proxy.mirrorOf(pv.charValue()))
          case Double => Some(pv => proxy.mirrorOf(pv.doubleValue()))
          case Float => Some(pv => proxy.mirrorOf(pv.floatValue()))
          case Int => Some(pv => proxy.mirrorOf(pv.intValue()))
          case Long => Some(pv => proxy.mirrorOf(pv.longValue()))
          case Short => Some(pv => proxy.mirrorOf(pv.shortValue()))
          case _ => None
        }
      }
    }

    val tpe = inReadAction(rawType.removeAliasDefinitions().widenIfLiteral)
    val stdTypes = tpe.getProject.stdTypes
    import stdTypes._

    val value = operandEvaluator.evaluate(context).asInstanceOf[Value]

    def message: String = {
      val valueType = value.`type`().name() match {
        case "boolean" => "Boolean"
        case "byte" => "Byte"
        case "char" => "Char"
        case "double" => "Double"
        case "float" => "Float"
        case "int" => "Int"
        case "long" => "Long"
        case "short" => "Short"
        case other => other
      }

      val castType = tpe match {
        case Boolean => "Boolean"
        case Byte => "Byte"
        case Char => "Char"
        case Double => "Double"
        case Float => "Float"
        case Int => "Int"
        case Long => "Long"
        case Short => "Short"
        case _ => inReadAction(DebuggerUtil.getJVMQualifiedName(tpe).getDisplayName(context.getDebugProcess))
      }

      DebuggerBundle.message("error.cannot.cast.value.to.type", valueType, castType)
    }

    (value, tpe) match {
      case (_, _: ScCompoundType) => value
      case (null, _) if tpe.isPrimitive =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.cannot.cast.null", tpe.canonicalText))
      case (null, _) => null
      case (b: BooleanValue, Boolean) => b
      case (b: ByteValue, NumericType(fn)) => fn(b)
      case (c: CharValue, NumericType(fn)) => fn(c)
      case (d: DoubleValue, NumericType(fn)) => fn(d)
      case (f: FloatValue, NumericType(fn)) => fn(f)
      case (i: IntegerValue, NumericType(fn)) => fn(i)
      case (l: LongValue, NumericType(fn)) => fn(l)
      case (s: ShortValue, NumericType(fn)) => fn(s)
      case (_: PrimitiveValue, _) =>
        throw EvaluationException(message)
      case (o: ObjectReference, _) =>
        val valueType = o.referenceType()
        val castType = new ClassOfEvaluator(tpe).evaluate(context).reflectedType()
        if (DebuggerUtilsImpl.instanceOf(valueType, castType)) o
        else throw EvaluationException(message)
    }
  }
}
