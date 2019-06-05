package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator

import java.{lang => jl}

import com.intellij.debugger.engine.evaluation.{EvaluationContextImpl, expression}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, api}

/**
 * User: Alefas
 * Date: 19.10.11
 */
final class ScalaLiteralEvaluator private(value: AnyRef,
                                          `type`: ScType)
  extends expression.Evaluator {

  import util.DebuggerUtil._

  override def evaluate(context: EvaluationContextImpl): AnyRef = value match {
    case null => null
    case _ =>
      val vm = context.getDebugProcess.getVirtualMachineProxy
      value match {
        case s: String => vm.mirrorOf(s)
        case b: jl.Boolean => createValue(vm, `type`, b.booleanValue())
        case c: jl.Character => createValue(vm, `type`, c.charValue())
        case f: jl.Float => createValue(vm, `type`, f.floatValue())
        case d: jl.Double => createValue(vm, `type`, d.doubleValue())
        case n: jl.Number => createValue(vm, `type`, n.longValue())
        case _ => throw EvaluationException("unknown type of literal")
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
        throw EvaluationException(s"Literal ${literal.getText} has null value")
      case _ => new ScalaLiteralEvaluator(value, `type`)
    }
  }
}