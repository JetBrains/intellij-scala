package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, EmptyDcl}

/*
 * ExistentialDclSeq ::= ExistentialDcl {semi ExistentialDcl}
 *
 * ExistentialDcl ::= 'type' TypeDcl
 *                  | 'val' ValDcl
 */

object ExistentialDclSeq extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE | ScalaTokenTypes.kVAL =>
        if (!Dcl(isMod = false)) {
          EmptyDcl(isMod = false)
        }
      case _ =>
        //builder error ScalaBundle.message("wrong.existential.declaration")
        return true
    }
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON || builder.newlineBeforeCurrentToken) {
      if (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) builder.advanceLexer() //Ate semi
      builder.getTokenType match {
        case ScalaTokenTypes.kTYPE | ScalaTokenTypes.kVAL =>
          if (!Dcl(isMod = false)) {
            EmptyDcl(isMod = false)
          }
        case _ =>
          builder error ScalaBundle.message("wrong.existential.declaration")
          builder.advanceLexer()
      }
    }
    true
  }
}