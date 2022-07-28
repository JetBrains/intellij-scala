package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.kOVERRIDE
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[Modifier]] ::= 'override'
 * | [[OpaqueModifier]]
 * | [[LocalModifier]]
 * | [[AccessModifier]]
 */
object Modifier extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case `kOVERRIDE` =>
      builder.advanceLexer() // Ate override
      true
    case _ =>
      OpaqueModifier() ||
        LocalModifier() ||
        AccessModifier()
  }
}