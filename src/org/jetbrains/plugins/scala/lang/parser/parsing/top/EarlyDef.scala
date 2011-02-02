package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import lexer.ScalaTokenTypes
import statements.PatVarDef
import builder.ScalaPsiBuilder
import annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * EarlyDef ::= '{' [PatVarDef {semi PatVarDef}] '}' 'with'
 */
object EarlyDef {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val earlyMarker = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer //Ate {
        builder.enableNewlines
      case _ => {
        builder error ScalaBundle.message("unreachable.error")
        earlyMarker.drop
        return false
      }
    }
    //this metod parse recursively PatVarDef {semi PatVarDef}
    @tailrec
    def subparse: Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE => {
          builder.advanceLexer //Ate }
          return true
        }
        case _ => {
          if (PatVarDef parse builder) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer //Ate }
                return true
              }
              case ScalaTokenTypes.tSEMICOLON => {
                builder.advanceLexer //Ate semicolon
                return subparse
              }
              case _ => {
                if (builder.newlineBeforeCurrentToken) {
                  return subparse
                }
                return false
              }
            }
          }
          else {
            return false
          }
        }
      }
    }
    if (!subparse) {
      builder.restoreNewlinesState
      builder error ScalaBundle.message("unreachable.error")
      earlyMarker.rollbackTo
      return false
    }
    builder.restoreNewlinesState
    //finally look for 'with' keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kWITH => {
        earlyMarker.done(ScalaElementTypes.EARLY_DEFINITIONS)
        builder.advanceLexer //Ate with
        return true
      }
      case _ => {
        builder error ScalaBundle.message("unreachable.error")
        earlyMarker.rollbackTo
        return false
      }
    }
  }
}