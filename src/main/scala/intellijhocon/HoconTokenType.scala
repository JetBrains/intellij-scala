package intellijhocon

import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

sealed class HoconTokenType(debugString: String) extends IElementType(debugString, HoconLanguage)

object HoconTokenType extends TokenType {

  case object LBrace extends HoconTokenType("LBRACE")

  case object RBrace extends HoconTokenType("RBRACE")

  case object LBracket extends HoconTokenType("LBRACKET")

  case object RBracket extends HoconTokenType("RBRACKET")

  case object Colon extends HoconTokenType("COLON")

  case object Comma extends HoconTokenType("COMMA")

  case object Equals extends HoconTokenType("EQUALS")

  case object PlusEquals extends HoconTokenType("PLUS_EQUALS")

  case object Dot extends HoconTokenType("DOT")

  case object NewLine extends HoconTokenType("NEWLINE")

  case object RefStart extends HoconTokenType("REF_START")

  case object RefEnd extends HoconTokenType("REF_END")

  case object Null extends HoconTokenType("NULL")

  case object True extends HoconTokenType("TRUE")

  case object False extends HoconTokenType("FALSE")

  case object Comment extends HoconTokenType("COMMENT")

  case object Integer extends HoconTokenType("INTEGER")

  case object Decimal extends HoconTokenType("DECIMAL")

  case object UnquotedString extends HoconTokenType("UNQUOTED_STRING")

  case object QuotedString extends HoconTokenType("QUOTED_STRING")

  case object MultilineString extends HoconTokenType("MULTILINE_STRING")

}
