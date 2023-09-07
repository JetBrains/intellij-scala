package org.jetbrains.plugins.scalaDirective.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes

sealed trait ScalaDirectiveValueKind {
  def wrap(text: String): String = this match {
    case ScalaDirectiveValueKind.InDoubleQuotes =>
      wrap(text, ScalaDirectiveValueKind.DoubleQuote)
    case ScalaDirectiveValueKind.InBackticks =>
      wrap(text, ScalaDirectiveValueKind.Backtick)
    case ScalaDirectiveValueKind.Plain => text
  }

  private def wrap(text: String, char: Char): String = s"$char$text$char"
}

object ScalaDirectiveValueKind {
  private val Backtick = '`'
  private val DoubleQuote = '"'

  def unapply(element: PsiElement): Option[(String, ScalaDirectiveValueKind)] =
    Option.when(element.getNode.getElementType == ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE)(extract(element.getText))

  def extract(text: String): (String, ScalaDirectiveValueKind) =
    if (text.length < 2) (text, Plain)
    else text.head match {
      case first if first != text.last => (text, Plain)
      case Backtick                    => (unwrap(text), InBackticks)
      case DoubleQuote                 => (unwrap(text), InDoubleQuotes)
      case _                           => (text, Plain)
    }

  private[this] def unwrap(text: String): String =
    text.substring(1, text.length - 1)

  case object Plain          extends ScalaDirectiveValueKind
  case object InBackticks    extends ScalaDirectiveValueKind
  case object InDoubleQuotes extends ScalaDirectiveValueKind
}
