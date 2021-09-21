package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[PatVar]] ::= varId | '_'
 */
object PatVar extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case `tIDENTIFIER` if builder.isIdBinding =>
      builder.advanceLexer() //Ate id
      true
    case `tUNDER` =>
      builder.advanceLexer() //Ate _
      true
    case _ => false
  }
}
