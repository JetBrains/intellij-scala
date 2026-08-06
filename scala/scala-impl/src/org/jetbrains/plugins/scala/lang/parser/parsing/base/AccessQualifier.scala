package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * AccessModifier ::= '[' (id | 'this') ']'
 */
object AccessQualifier extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.tLSQBRACKET =>
      builder.advanceLexer() // Ate [
      builder.disableNewlines()
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kTHIS =>
          builder.advanceLexer() // Ate identifier or this
        case _ => builder.error(ErrMsg("identifier.expected"))
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tRSQBRACKET => builder.advanceLexer() // Ate ]
        case _ => builder.error(ErrMsg("rsqbracket.expected"))
      }
      builder.restoreNewlinesState()
      true
    case _ => false
  }
}
