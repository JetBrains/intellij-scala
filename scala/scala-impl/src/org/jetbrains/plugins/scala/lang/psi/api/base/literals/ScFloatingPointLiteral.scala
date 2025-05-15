package org.jetbrains.plugins.scala.lang.psi.api.base.literals

import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatingPointLiteral.{FloatingPointParseResult, zeroFloatingPointRegex}

import java.lang.{Double => JDouble, Float => JFloat}

trait ScFloatingPointLiteral extends ScLiteral.Numeric {

  final def floatingPointParseResult: FloatingPointParseResult = {
    getValue match {
      case null => FloatingPointParseResult.Malformed
      case double: JDouble if double.isInfinite => FloatingPointParseResult.TooLarge
      case float: JFloat if float.isInfinite => FloatingPointParseResult.TooLarge
      case 0.0 if !zeroFloatingPointRegex.matches(getText) => FloatingPointParseResult.TooSmall
      case _ => FloatingPointParseResult.Ok
    }
  }
}

object ScFloatingPointLiteral {
  // adapted from the compiler: https://github.com/scala/scala3/blob/main/library/src/scala/util/FromDigits.scala#L122
  private val zeroFloatingPointRegex = raw"-?[0._]+(?:[eE][+-]?[0-9_]+)?[fFdD]?".r

  abstract class FloatingPointParseResult
  object FloatingPointParseResult {
    case object Ok extends FloatingPointParseResult
    case object Malformed extends FloatingPointParseResult
    case object TooLarge extends FloatingPointParseResult
    case object TooSmall extends FloatingPointParseResult
  }

  trait Companion[T <: ScFloatingPointLiteral] extends ScLiteral.NumericCompanion[T]
}
