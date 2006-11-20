package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

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
//        marker.done(ScalaTokenTypes.tINTEGER)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tFLOAT => { //Floating point literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
//        marker.done(ScalaTokenTypes.tFLOAT)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => { //Boolean Literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
//        marker.done(ScalaElementTypes.BOOLEAN_LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tCHAR => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
//        marker.done(ScalaTokenTypes.tCHAR)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.kNULL => { //null literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
//        marker.done(ScalaTokenTypes.kNULL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tSTRING => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
//        marker.done(ScalaTokenTypes.tCHAR)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tWRONG_STRING => { //Character literal
        ParserUtils.eatElement(builder, ScalaElementTypes.LITERAL)
        builder.error("Wrong string literal")
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }

/*
      case ScalaTokenTypes.tSTRING_BEGIN => { //String literal
//        val beginMarker = builder.mark();
        ParserUtils.eatElement(builder, ScalaTokenTypes.tSTRING_BEGIN)
//        beginMarker.done(ScalaTokenTypes.tSTRING_BEGIN)
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING_END => {
//            val strContentMarker = builder.mark()
//            strContentMarker.done(ScalaTokenTypes.tSTRING)
            ParserUtils.eatElement(builder, ScalaTokenTypes.tSTRING_END)
          }
          case ScalaTokenTypes.tSTRING => {
            ParserUtils.eatElement(builder,ScalaTokenTypes.tSTRING)
            builder.getTokenType match {
              case ScalaTokenTypes.tSTRING_END => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tSTRING_END)
              }
              case _ => builder.error("Wrong string completion")
            }
          }
          case _ => builder.error("Wrong string declaration")
        }
//        marker.done(ScalaElementTypes.STRING_LITERAL)
        marker.done(ScalaElementTypes.LITERAL)
        ScalaElementTypes.LITERAL
      }
*/

      case _ => {
        marker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }
  }

}
