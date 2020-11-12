package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

import scala.annotation.tailrec


/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Refinement ::= [nl] '{' Refinestat {semi RefineStat} '}'
 */
object Refinement extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean =
    parseRefinements(hadOneRefinement = false)

  @tailrec
  private def parseRefinements(hadOneRefinement: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken =>
        val refineMarker = builder.mark

        builder.advanceLexer() //Ate {
        builder.enableNewlines()

        ParserUtils.parseLoopUntilRBrace(builder, () => RefineStatSeq parse builder)
        builder.restoreNewlinesState()
        refineMarker.done(ScalaElementType.REFINEMENT)

        if (builder.isScala3) {
          parseRefinements(hadOneRefinement = true)
        } else true
      case _ =>
        hadOneRefinement
    }
  }
}