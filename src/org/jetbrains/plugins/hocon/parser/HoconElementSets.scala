package org.jetbrains.plugins.hocon.parser

import com.intellij.psi.TokenType

object HoconElementSets {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  val KeyedField = PrefixedField | ValuedField
  val Literal = Null | Boolean | Number | StringValue
  val Value = Literal | Object | Array | Substitution | Concatenation
  val ForcedLeafBlock = Key | Path | UnquotedString | Number | Null | Boolean | TokenType.ERROR_ELEMENT
}
