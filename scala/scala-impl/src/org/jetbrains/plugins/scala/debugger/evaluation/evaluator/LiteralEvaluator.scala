package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType

import java.util.Collections

/**
 * Scala translation of the platform LiteralEvaluator. There are no implementation differences apart from
 * the syntax.
 */
private final class LiteralEvaluator private(value: AnyRef, expectedType: String) extends ValueEvaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    val vm = context.getDebugProcess.getVirtualMachineProxy

    value match {
      case null => null
      case b: java.lang.Boolean => DebuggerUtilsEx.createValue(vm, expectedType, b.booleanValue())
      case c: java.lang.Character => DebuggerUtilsEx.createValue(vm, expectedType, c.charValue())
      case d: java.lang.Double => DebuggerUtilsEx.createValue(vm, expectedType, d.doubleValue())
      case f: java.lang.Float => DebuggerUtilsEx.createValue(vm, expectedType, f.floatValue())
      case n: java.lang.Number => DebuggerUtilsEx.createValue(vm, expectedType, n.longValue())
      case s: String =>
        vm.mirrorOfStringLiteral(s, () => {
          val ref = DebuggerUtilsEx.mirrorOfString(s, vm, context)
          if (Registry.is("debugger.intern.string.literals") && vm.versionHigher("1.7")) {
            val internMethod = DebuggerUtils.findMethod(ref.referenceType(), "intern", "()Ljava/lang/String;")
            if (internMethod ne null) {
              context.getDebugProcess.invokeMethod(context, ref, internMethod, Collections.emptyList()).asInstanceOf[StringReference]
            } else ref
          } else ref
        })
      case _ =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", expectedType))
    }
  }
}

private[evaluation] object LiteralEvaluator {

  /**
   * Constructs a [[LiteralEvaluator]] from an [[ScLiteral]] by extracting the type of the literal. Any literal types
   * are widened to their platform types (for primitives and strings).
   *
   * An `expected` type is then constructed based on the extracted type of the literal. This expected type is used
   * by the platform to create a numeric value of the specified type. This `expected` type is ignored for string
   * literals.
   *
   * The value of the literal and the expected type are then passed to the platform implementation.
   */
  def create(lit: ScLiteral): ValueEvaluator = {
    val tpe = lit.`type`().getOrAny match {
      case lt: ScLiteralType => lt.wideType
      case t => t
    }

    val stdTypes = tpe.projectContext.stdTypes
    import stdTypes._

    val expected = tpe match {
      case Boolean => "boolean"
      case Byte => "byte"
      case Char => "char"
      case Double => "double"
      case Float => "float"
      case Int => "int"
      case Long => "long"
      case Short => "short"
      case _ => ""
    }

    new LiteralEvaluator(lit.getValue, expected)
  }
}
