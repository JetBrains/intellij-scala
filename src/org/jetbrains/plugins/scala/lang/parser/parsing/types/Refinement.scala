package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import builder.ScalaPsiBuilder


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
    if (builder.countNewlineBeforeCurrentToken > 1) {
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
    RefineStatSeq parse builder
    builder.getTokenType match {
      case ScalaTokenTypes.tRBRACE => {
        builder.advanceLexer //Ate }
      }
      case _ => {
        builder error ScalaBundle.message("rbrace.expected")
      }
    }
    builder.restoreNewlinesState
    refineMarker.done(ScalaElementTypes.REFINEMENT)
    return true
  }
}