package org.jetbrains.plugins.scala.lang.psi.api.base.literals

import com.intellij.psi.util.PsiLiteralUtil
import com.intellij.util.text.LiteralFormatUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatingPointLiteral.FloatingPointParseResult

trait ScFloatingPointLiteral extends ScLiteral.Numeric {
  def floatingPointParseResult: FloatingPointParseResult
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

    def parseFloat(text: String): FloatingPointParseResult =
      PsiLiteralUtil.parseFloat(LiteralFormatUtil.removeUnderscores(text)) match {
        case null => FloatingPointParseResult.Malformed
        case float if float == 0.0f && !zeroFloatingPointRegex.matches(text) => FloatingPointParseResult.TooSmall
        case float if float.isInfinite => FloatingPointParseResult.TooLarge
        case _ => FloatingPointParseResult.Ok
      }

    def parseDouble(text: String): FloatingPointParseResult =
      PsiLiteralUtil.parseDouble(LiteralFormatUtil.removeUnderscores(text)) match {
        case null => FloatingPointParseResult.Malformed
        case double if double == 0.0 && !zeroFloatingPointRegex.matches(text) => FloatingPointParseResult.TooSmall
        case double if double.isInfinite => FloatingPointParseResult.TooLarge
        case _ => FloatingPointParseResult.Ok
      }
  }

  trait Companion[T <: ScFloatingPointLiteral] extends ScLiteral.NumericCompanion[T]
}
