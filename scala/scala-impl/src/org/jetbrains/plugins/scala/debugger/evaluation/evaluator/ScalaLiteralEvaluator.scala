package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator

import com.intellij.debugger.engine.evaluation.{EvaluationContextImpl, expression}
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, api}

import java.{lang => jl}

final class ScalaLiteralEvaluator private[evaluator] (value: AnyRef,
                                          `type`: ScType)
  extends expression.Evaluator {

  override def evaluate(context: EvaluationContextImpl): Value = value match {
    case null => null
    case _ =>
      val vm = context.getDebugProcess.getVirtualMachineProxy
      val types = `type`.projectContext.stdTypes
      import types._


      value match {
        case s: String => vm.mirrorOf(s)
        case b: jl.Boolean =>
          `type` match {
            case Boolean => vm.mirrorOf(b.booleanValue())
            case Unit => vm.mirrorOfVoid()
            case _ => null
          }
        case c: jl.Character =>
          val char = c.charValue()
          `type` match {
            case Long => vm.mirrorOf(char)
            case Int => vm.mirrorOf(char.toInt)
            case Byte => vm.mirrorOf(char.toByte)
            case Short => vm.mirrorOf(char.toShort)
            case Char => vm.mirrorOf(char.toChar)
            case Float => vm.mirrorOf(char.toFloat)
            case Double => vm.mirrorOf(char.toDouble)
            case Unit => vm.mirrorOfVoid()
            case _ => null
          }
        case f: jl.Float =>
          val float = f.floatValue()
          `type` match {
            case Long => vm.mirrorOf(float)
            case Int => vm.mirrorOf(float.toInt)
            case Byte => vm.mirrorOf(float.toByte)
            case Short => vm.mirrorOf(float.toShort)
            case Char => vm.mirrorOf(float.toChar)
            case Float => vm.mirrorOf(float.toFloat)
            case Double => vm.mirrorOf(float.toDouble)
            case Unit => vm.mirrorOfVoid()
            case _ => null
          }
        case d: jl.Double =>
          val double = d.doubleValue()
          `type` match {
            case Long => vm.mirrorOf(double)
            case Int => vm.mirrorOf(double.toInt)
            case Byte => vm.mirrorOf(double.toByte)
            case Short => vm.mirrorOf(double.toShort)
            case Char => vm.mirrorOf(double.toChar)
            case Float => vm.mirrorOf(double.toFloat)
            case Double => vm.mirrorOf(double.toDouble)
            case Unit => vm.mirrorOfVoid()
            case _ => null
          }

        case n: jl.Number =>
          val long = n.longValue()
          `type` match {
            case Long => vm.mirrorOf(long)
            case Int => vm.mirrorOf(long.toInt)
            case Byte => vm.mirrorOf(long.toByte)
            case Short => vm.mirrorOf(long.toShort)
            case Char => vm.mirrorOf(long.toChar)
            case Float => vm.mirrorOf(long.toFloat)
            case Double => vm.mirrorOf(long.toDouble)
            case Unit => vm.mirrorOfVoid()
            case _ => null
          }
        case _ => throw EvaluationException(ScalaBundle.message("unknown.type.of.literal"))
      }
  }
}

object ScalaLiteralEvaluator {

  def empty(implicit context: project.ProjectContext) =
    new ScalaLiteralEvaluator(null, api.Null)

  def apply(literal: ScLiteral,
            value: AnyRef): ScalaLiteralEvaluator = {
    val `type` = literal.`type`().getOrAny match {
      case literalType: ScLiteralType => literalType.wideType
      case scType => scType
    }

    value match {
      case null if !`type`.isNull =>
        throw EvaluationException(ScalaBundle.message("literal.has.null.value", literal.getText))
      case _ => new ScalaLiteralEvaluator(value, `type`)
    }
  }
}