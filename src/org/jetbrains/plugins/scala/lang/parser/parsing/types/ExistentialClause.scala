package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * ExistentialClause ::= 'forSome' '{' ExistentialDcl {semi ExistentialDcl} '}'
 */

object ExistentialClause {
  def parse(builder: ScalaPsiBuilder) : Boolean = {
    val existMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME => {
        builder.advanceLexer() //Ate forSome
      }
      case _ => {
        existMarker.drop()
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer() //Ate {
        builder.enableNewlines
      }
      case _ => {
        builder error ScalaBundle.message("existential.block.expected")
        existMarker.done(ScalaElementTypes.EXISTENTIAL_CLAUSE)
        return true
      }
    }
    def foo() {
      ExistentialDclSeq parse builder
    }
    ParserUtils.parseLoopUntilRBrace(builder, foo _)
    builder.restoreNewlinesState
    existMarker.done(ScalaElementTypes.EXISTENTIAL_CLAUSE)
    true
  }
}