package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  LocalModifier ::= abstract
 *                  | final
 *                  | sealed
 *                  | implicit
 *                  | lazy
 */

object LocalModifier extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.kABSTRACT | ScalaTokenTypes.kFINAL
         | ScalaTokenTypes.kSEALED | ScalaTokenTypes.kIMPLICIT
         | ScalaTokenTypes.kLAZY =>
      builder.advanceLexer() // Ate modifier
      true
    case _ if builder.getTokenText == ScalaTokenType.InlineKeyword.text =>
      SoftModifier()
    case _ => false
  }
}