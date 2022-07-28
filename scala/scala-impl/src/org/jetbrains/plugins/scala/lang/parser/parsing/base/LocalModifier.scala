package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[LocalModifier]] ::= 'abstract'
 * | 'final'
 * | 'sealed'
 * | 'implicit'
 * | 'lazy'
 * | [[LocalSoftModifier]]
 */
object LocalModifier extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case `kABSTRACT` |
         `kFINAL` |
         `kSEALED` |
         `kIMPLICIT` |
         `kLAZY` =>
      builder.advanceLexer() // Ate modifier
      true
    case _ => LocalSoftModifier()
  }
}