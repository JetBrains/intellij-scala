package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator


/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Refinement ::= [nl] '{' Refinestat {semi RefineStat} '}'
 */

object Refinement {
  def parse(builder: PsiBuilder): Boolean = {
    val refineMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        if (LineTerminator(builder.getTokenText)) {
          builder.advanceLexer //Ate nl
        }
        else {
          refineMarker.rollbackTo
          return false
        }
      }
      case _ => {}
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate {
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
    refineMarker.done(ScalaElementTypes.REFINEMENT)
    return true
  }
}