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
 *   Modifier ::= LocalModifier
 *             | override
 *             | AccessModifier
 */

object Modifier {
  def parse(builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.kOVERRIDE =>
      builder.advanceLexer() // Ate override
      true
    case _ => LocalModifier.parse(builder) || AccessModifier.parse(builder)
  }
}