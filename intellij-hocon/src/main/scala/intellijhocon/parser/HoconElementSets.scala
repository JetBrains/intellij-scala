package intellijhocon
package parser

import com.intellij.psi.TokenType

object HoconElementSets {

  import intellijhocon.Util._
  import intellijhocon.lexer.HoconTokenType._
  import intellijhocon.parser.HoconElementType._

  val Path = FieldPath | SubstitutionPath
  val StringValue = UnquotedString | QuotedString | MultilineString
  val ForcedLeafBlock = Path | UnquotedString | Number | Null | Boolean | TokenType.ERROR_ELEMENT
}
