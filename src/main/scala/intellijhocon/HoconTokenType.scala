package intellijhocon

import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

sealed class HoconTokenType(debugString: String) extends IElementType(debugString, HoconLanguage)

object HoconTokenType extends TokenType {

  val Whitespace = new HoconTokenType("WHITESPACE")

  val BadCharacter = new HoconTokenType("BAD_CHARACTER")

  val LBrace = new HoconTokenType("LBRACE")

  val RBrace = new HoconTokenType("RBRACE")

  val LBracket = new HoconTokenType("LBRACKET")

  val RBracket = new HoconTokenType("RBRACKET")

  val Colon = new HoconTokenType("COLON")

  val Comma = new HoconTokenType("COMMA")

  val Equals = new HoconTokenType("EQUALS")

  val PlusEquals = new HoconTokenType("PLUS_EQUALS")

  val Period = new HoconTokenType("DOT")

  val NewLine = new HoconTokenType("NEWLINE")

  val Dollar = new HoconTokenType("DOLLAR")

  val RefLBrace = new HoconTokenType("REF_LBRACE")

  val QMark = new HoconTokenType("QMARK")

  val RefRBrace = new HoconTokenType("REF_RBRACE")

  val Comment = new HoconTokenType("COMMENT")

  val UnquotedChars = new HoconTokenType("UNQUOTED_CHARS")

  val QuotedString = new HoconTokenType("QUOTED_STRING")

  val MultilineString = new HoconTokenType("MULTILINE_STRING")

}
