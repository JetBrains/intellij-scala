package intellijhocon.parser

import intellijhocon.Util
import com.intellij.psi.TokenType

object HoconElementSets {

  import HoconElementType._
  import Util._

  val Path = FieldPath | ReferencePath
  val ForcedLeafBlock = Path | UnquotedString | Number | Null | Boolean | TokenType.ERROR_ELEMENT
}
