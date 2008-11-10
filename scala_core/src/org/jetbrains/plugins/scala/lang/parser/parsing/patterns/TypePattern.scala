package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.{ExistentialClause, InfixType, Type}

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * TypePattern ::= Type (but it can't be InfixType => Type (because case A => B => C?))
 */

object TypePattern {
  def parse(builder: PsiBuilder): Boolean = {
    val typeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        val parMarker = builder.mark
        builder.advanceLexer //Ate (
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE | ScalaTokenTypes.tRPARENTHESIS => {
            if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
              builder.advanceLexer //Ate =>
              if (!Type.parse(builder,false,true)) {
                builder error ScalaBundle.message("wrong.type")
              }
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ScalaBundle.message("rparenthesis.expected")
              }
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE => {
                builder.advanceLexer //Ate =>
              }
              case _ => {
                builder error ScalaBundle.message("fun.sign.expected")
              }
            }
            if (!Type.parse(builder,false,true)) {
              builder error ScalaBundle.message("wrong.type")
            }
            typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
            parMarker.drop
            return true
          }
          case _ => {parMarker.rollbackTo}
        }
      }
      case _ => {}
    }
    if (!InfixType.parse(builder,false,true)) {
      typeMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME => {
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
        return true
      }
      case _ => {
        typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
        return true
      }
    }
  }
}