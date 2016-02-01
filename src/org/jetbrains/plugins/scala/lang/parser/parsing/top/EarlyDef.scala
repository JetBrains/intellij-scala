package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.PatVarDef

import scala.annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * EarlyDef ::= '{' [PatVarDef {semi PatVarDef}] '}' 'with'
 */
object EarlyDef extends EarlyDef {
  override protected val patVarDef = PatVarDef
}

trait EarlyDef {
  protected val patVarDef: PatVarDef

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val earlyMarker = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines
      case _ =>
        builder error ScalaBundle.message("unreachable.error")
        earlyMarker.drop()
        return false
    }
    //this metod parse recursively PatVarDef {semi PatVarDef}
    @tailrec
    def subparse: Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE =>
          builder.advanceLexer() //Ate }
          true
        case _ =>
          if (patVarDef parse builder) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer() //Ate }
                true
              }
              case ScalaTokenTypes.tSEMICOLON => {
                builder.advanceLexer() //Ate semicolon
                subparse
              }
              case _ => {
                if (builder.newlineBeforeCurrentToken) {
                  subparse
                } else {
                  false
                }
              }
            }
          }
          else {
            false
          }
      }
    }
    if (!subparse) {
      builder.restoreNewlinesState
      builder error ScalaBundle.message("unreachable.error")
      earlyMarker.rollbackTo()
      return false
    }
    builder.restoreNewlinesState
    //finally look for 'with' keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kWITH =>
        earlyMarker.done(ScalaElementTypes.EARLY_DEFINITIONS)
        builder.advanceLexer() //Ate with
        true
      case _ =>
        builder error ScalaBundle.message("unreachable.error")
        earlyMarker.rollbackTo()
        false
    }
  }
}