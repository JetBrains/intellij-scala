package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{Dcl, EmptyDcl}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * ExistentialDclSeq ::= ExistentialDcl {semi ExistentialDcl}
 *
 * ExistentialDcl ::= 'type' TypeDcl
 *                  | 'val' ValDcl
 */

object ExistentialDclSeq {
  def parse(builder: ScalaPsiBuilder): Unit = {
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE | ScalaTokenTypes.kVAL =>
        if (!Dcl.parse(builder, isMod = false)) {
          EmptyDcl.parse(builder, isMod = false)
        }
      case _ =>
        //builder error ScalaBundle.message("wrong.existential.declaration")
        return
    }
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON || builder.newlineBeforeCurrentToken) {
      if (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) builder.advanceLexer() //Ate semi
      builder.getTokenType match {
        case ScalaTokenTypes.kTYPE | ScalaTokenTypes.kVAL =>
          if (!Dcl.parse(builder, isMod = false)) {
            EmptyDcl.parse(builder, isMod = false)
          }
        case _ =>
          builder error ScalaBundle.message("wrong.existential.declaration")
          builder.advanceLexer()
      }
    }
  }
}