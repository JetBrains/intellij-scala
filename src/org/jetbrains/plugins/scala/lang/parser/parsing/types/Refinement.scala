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
 * Refinement ::= [nl] '{' Refinestat {semi RefineStat} '}'
 */

object Refinement {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val refineMarker = builder.mark
    if (builder.twoNewlinesBeforeCurrentToken) {
      refineMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate {
        builder.enableNewlines
      }
      case _ => {
        refineMarker.rollbackTo
        return false
      }
    }
    def foo() {
      RefineStatSeq parse builder
    }
    ParserUtils.parseLoopUntilRBrace(builder, foo _)
    builder.restoreNewlinesState
    refineMarker.done(ScalaElementTypes.REFINEMENT)
    return true
  }
}