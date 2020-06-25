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
 *   Modifier ::= LocalModifier
 *             | ‘override‘
 *             | ‘opaque’   (in scala 3)
 *             | AccessModifier
 */

object Modifier extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.kOVERRIDE =>
      builder.advanceLexer() // Ate override
      true
    case _ if builder.getTokenText == ScalaTokenType.OpaqueKeyword.text =>
      SoftModifier()
    case _ => LocalModifier.parse(builder) || AccessModifier.parse(builder)
  }
}