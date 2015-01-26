package org.jetbrains.plugins.hocon.parser

import com.intellij.psi.TokenType

object HoconElementSets {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  val Path = FieldPath | SubstitutionPath
  val Literal = Null | Boolean | Number | String
  val Value = Literal | Object | Array | Substitution | Concatenation
  val ForcedLeafBlock = Path | UnquotedString | Number | Null | Boolean | TokenType.ERROR_ELEMENT
}
