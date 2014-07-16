package intellijhocon
package parser

import intellijhocon.Util
import com.intellij.psi.TokenType
import intellijhocon.lexer.HoconTokenType

object HoconElementSets {

  import HoconElementType._
  import HoconTokenType._
  import Util._

  val Path = FieldPath | SubstitutionPath
  val StringValue = UnquotedString | QuotedString | MultilineString
  val ForcedLeafBlock = Path | UnquotedString | Number | Null | Boolean | TokenType.ERROR_ELEMENT
}
