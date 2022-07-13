package org.jetbrains.plugins.scala.debugger.evaluation.modern

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.{StringReference, Value}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

import java.util.Collections
import scala.annotation.tailrec

/**
 * Scala translation of [[com.intellij.debugger.engine.evaluation.expression.LiteralEvaluator]].
 * The original class is unfortunately package-private.
 */
private final class LiteralEvaluator(value: AnyRef, expectedType: String) extends Evaluator {
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
          val str = DebuggerUtilsEx.mirrorOfString(s, vm, context)
          // intern starting from jdk 7
          if (Registry.is("debugger.intern.string.literals") && vm.versionHigher("1.7")) {
            val internMethod = DebuggerUtils.findMethod(str.referenceType(), "intern", "()Ljava/lang/String;")
            if (internMethod ne null) {
              context.getDebugProcess.invokeMethod(context, str, internMethod, Collections.emptyList()).asInstanceOf[StringReference]
            }
          }
          str
        })
      case _ =>
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", expectedType))
    }
  }

  override def toString: String =
    if (value ne null) value.toString else "null"
}

private object LiteralEvaluator {
  def create(literal: ScLiteral): LiteralEvaluator = {
    val value = literal.getValue
    val expectedType = extractExpectedType(literal.`type`().getOrAny)
    new LiteralEvaluator(value, expectedType)
  }

  @tailrec
  private def extractExpectedType(tpe: ScType): String = {
    val stdTypes = tpe.projectContext.stdTypes
    import stdTypes._

    tpe match {
      case lt: ScLiteralType => extractExpectedType(lt.wideType)
      case Boolean => "boolean"
      case Byte => "byte"
      case Char => "char"
      case Double => "double"
      case Float => "float"
      case Int => "int"
      case Long => "long"
      case Short => "short"
      case _ => "java.lang.String"
    }
  }
}
