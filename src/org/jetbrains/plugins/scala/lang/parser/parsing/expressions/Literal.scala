package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
Literal ::= ['-']integerLiteral
            | ['-']floatingPointLiteral
            | booleanLiteral
            | characterLiteral
            | stringLiteral
            | symbolLiteral
            | true
            | false
            | null
            | javaId"StringLiteral" 
*/
object Literal {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (builder.getTokenText == "-") {
          builder.advanceLexer() //Ate -
          builder.getTokenType match {
            case ScalaTokenTypes.tINTEGER |
                 ScalaTokenTypes.tFLOAT => {
              builder.advanceLexer() //Ate literal
              marker.done(ScalaElementTypes.LITERAL)
              true
            }
            case _ => {
              marker.rollbackTo()
              false
            }
          }
        }
        else {
          marker.rollbackTo()
          false
        }
      }
      case ScalaTokenTypes.tINTERPOLATED_STRING_ID =>
        CommonUtils.parseInterpolatedString(builder, isPattern = false)
        marker.done(ScalaElementTypes.INTERPOLATED_STRING_LITERAL)
        true
      case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING | ScalaTokenTypes.tINTERPOLATED_STRING =>
        builder.advanceLexer()
        marker.done(ScalaElementTypes.INTERPOLATED_STRING_LITERAL)
        true
      case ScalaTokenTypes.tINTEGER | ScalaTokenTypes.tFLOAT |
           ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE |
           ScalaTokenTypes.tCHAR | ScalaTokenTypes.tSYMBOL |
           ScalaTokenTypes.kNULL | ScalaTokenTypes.tSTRING |
           ScalaTokenTypes.tMULTILINE_STRING  => {
        builder.advanceLexer() //Ate literal
        marker.done(ScalaElementTypes.LITERAL)
        true
      }
      case ScalaTokenTypes.tWRONG_STRING => {
        //wrong string literal
        builder.advanceLexer() //Ate wrong string
        builder.error("Wrong string literal")
        marker.done(ScalaElementTypes.LITERAL)
        true
      }
      case _ => {
        marker.rollbackTo()
        false
      }
    }
  }
}