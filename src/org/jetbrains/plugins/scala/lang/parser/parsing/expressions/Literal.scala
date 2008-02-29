package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 11:08:30
* To change this template use File | Settings | File Templates.
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

object Literal{
  def parse(builder : PsiBuilder) : Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER  => {
        if (builder.getTokenText == "-") {
          builder.advanceLexer //Ate -
          builder.getTokenType match {
            case ScalaTokenTypes.tINTEGER |
                 ScalaTokenTypes.tFLOAT => {
              builder.advanceLexer //Ate literal
              marker.done(ScalaElementTypes.LITERAL)
              return true
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
           ScalaTokenTypes.kNULL | ScalaTokenTypes.tSTRING => {
        builder.advanceLexer //Ate literal
        marker.done(ScalaElementTypes.LITERAL)
        return true
      }
      case ScalaTokenTypes.tWRONG_STRING => { //wrong string literal
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