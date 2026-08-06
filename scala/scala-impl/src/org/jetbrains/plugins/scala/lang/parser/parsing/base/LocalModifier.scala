package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * {{{
 * LocalModifier ::= 'abstract'
 *                 | 'final'
 *                 | 'sealed'
 *                 | 'implicit'
 *                 | 'lazy'
 * }}}
 */
object LocalNonSoftModifier extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case `kABSTRACT` |
         `kFINAL` |
         `kSEALED` |
         `kIMPLICIT` |
         `kLAZY` =>
      builder.advanceLexer() // Ate modifier
      true
    case _ =>
      false
  }
}