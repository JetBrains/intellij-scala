package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  AccessModifier ::= ( 'private' | 'protected' ) [ AccessQualifier ]
 */
object AccessModifier {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kPRIVATE | ScalaTokenTypes.kPROTECTED =>
        builder.advanceLexer() // Ate modifier
        AccessQualifier.parse(builder)
        marker.done(ScalaElementTypes.ACCESS_MODIFIER)
        true
      case _ =>
        marker.drop()
        false
    }
  }
}