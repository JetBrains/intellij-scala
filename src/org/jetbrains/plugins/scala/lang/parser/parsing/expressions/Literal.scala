package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

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
*/

object Literal {
  def parse(builder: PsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (builder.getTokenText == "-") {
          builder.advanceLexer //Ate -
          builder.getTokenType match {
            case ScalaTokenTypes.tINTEGER |
                 ScalaTokenTypes.tFLOAT => {
              builder.advanceLexer //Ate literal
              marker.done(ScalaElementTypes.LITERAL)
              return true
            }
            case _ => {
              marker.rollbackTo
              return false
            }
          }
        }
        else {
          marker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.tINTEGER | ScalaTokenTypes.tFLOAT |
           ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE |
           ScalaTokenTypes.tCHAR | ScalaTokenTypes.tSYMBOL |
           ScalaTokenTypes.kNULL | ScalaTokenTypes.tSTRING |
           ScalaTokenTypes.tMULTILINE_STRING => {
        builder.advanceLexer //Ate literal
        marker.done(ScalaElementTypes.LITERAL)
        return true
      }
      case ScalaTokenTypes.tWRONG_STRING => {
        //wrong string literal
        builder.advanceLexer //Ate wrong string
        builder.error("Wrong string literal")
        marker.done(ScalaElementTypes.LITERAL)
        return true
      }
      case _ => {
        marker.rollbackTo()
        return false
      }
    }
  }
}