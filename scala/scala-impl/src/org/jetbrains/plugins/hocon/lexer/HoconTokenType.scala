package org.jetbrains.plugins.hocon.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.hocon.lang.HoconLanguage

sealed class HoconTokenType(debugString: String) extends IElementType(debugString, HoconLanguage)

object HoconTokenType extends TokenType {

  val InlineWhitespace = new HoconTokenType("INLINE_WHITESPACE")
  val LineBreakingWhitespace = new HoconTokenType("LINE_BREAKING_WHITESPACE")
  val BadCharacter = new HoconTokenType("BAD_CHARACTER")
  val LBrace = new HoconTokenType("LBRACE")
  val RBrace = new HoconTokenType("RBRACE")
  val LBracket = new HoconTokenType("LBRACKET")
  val RBracket = new HoconTokenType("RBRACKET")
  val Colon = new HoconTokenType("COLON")
  val Comma = new HoconTokenType("COMMA")
  val Equals = new HoconTokenType("EQUALS")
  val PlusEquals = new HoconTokenType("PLUS_EQUALS")
  val Period = new HoconTokenType("PERIOD")
  val Dollar = new HoconTokenType("DOLLAR")
  val SubLBrace = new HoconTokenType("SUB_LBRACE")
  val QMark = new HoconTokenType("QMARK")
  val SubRBrace = new HoconTokenType("SUB_RBRACE")
  val HashComment = new HoconTokenType("HASH_COMMENT")
  val DoubleSlashComment = new HoconTokenType("DOUBLE_SLASH_COMMENT")
  val UnquotedChars = new HoconTokenType("UNQUOTED_CHARS")
  val QuotedString = new HoconTokenType("QUOTED_STRING")
  val MultilineString = new HoconTokenType("MULTILINE_STRING")

}
