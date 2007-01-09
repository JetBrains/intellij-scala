package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._


object Literal{

/*
Literal ::= integerLiteral
            | floatingPointLiteral
            | booleanLiteral
            | characterLiteral
            | stringLiteral
            | symbolLiteral
            | true
            | false
            | null
*/

  def parse(builder : PsiBuilder) : ScalaElementType = {

    val marker = builder.mark()

    builder.getTokenType match{
      case ScalaTokenTypes.tINTEGER => { // Integer literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tFLOAT => { //Floating point literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => { //Boolean Literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tCHAR => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tSYMBOL => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.kNULL => { //null literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tSTRING => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tWRONG_STRING => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        builder.error("Wrong string literal")
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case _ => {
        marker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }
  }

}
