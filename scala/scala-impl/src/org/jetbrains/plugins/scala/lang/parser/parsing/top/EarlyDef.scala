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
object EarlyDef extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val earlyMarker = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()
      case _ =>
        builder error ScalaBundle.message("unreachable.error")
        earlyMarker.drop()
        return false
    }
    //this metod parse recursively PatVarDef {semi PatVarDef}
    @tailrec
    def parseSub(): Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE =>
          builder.advanceLexer() //Ate }
          true
        case _ =>
          if (PatVarDef()) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE =>
                builder.advanceLexer() //Ate }
                true
              case ScalaTokenTypes.tSEMICOLON =>
                builder.advanceLexer() //Ate semicolon
                parseSub()
              case _ =>
                if (builder.newlineBeforeCurrentToken) {
                  parseSub()
                } else {
                  false
                }
            }
          }
          else {
            false
          }
      }
    }
    if (!parseSub()) {
      builder.restoreNewlinesState()
      builder error ScalaBundle.message("unreachable.error")
      earlyMarker.rollbackTo()
      return false
    }
    builder.restoreNewlinesState()
    //finally look for 'with' keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kWITH =>
        earlyMarker.done(ScalaElementType.EARLY_DEFINITIONS)
        builder.advanceLexer() //Ate with
        true
      case _ =>
        builder error ScalaBundle.message("unreachable.error")
        earlyMarker.rollbackTo()
        false
    }
  }
}