package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils._

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ParamType ::= Type |
 *               '=>' Type |
 *               Type '*'
 */

object ParamType {
  def parseInner(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate '=>'
        Type.parse(builder)
      case _ =>
        if (!Type.parse(builder, star = true)) false else {
          builder.getTokenText match {
            case "*" => builder.advanceLexer() // Ate '*'
            case _ => /* nothing needs to be done */
          }
          true
        }
    }
  }

  def parse(builder: ScalaPsiBuilder) = build(ScalaElementTypes.PARAM_TYPE, builder) { parseInner(builder) }
}