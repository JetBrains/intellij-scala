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
        builder.advanceLexer()
//        marker.done(ScalaTokenTypes.tINTEGER)
        marker.drop()
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tFLOAT => { //Floating point literal
        builder.advanceLexer()
//        marker.done(ScalaTokenTypes.tFLOAT)
        marker.drop()
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => { //Boolean Literal
        builder.advanceLexer()        
//        marker.done(ScalaElementTypes.BOOLEAN_LITERAL)
        marker.drop()
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tCHAR => { //Character literal
        builder.advanceLexer()
//        marker.done(ScalaTokenTypes.tCHAR)
        marker.drop()
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.kNULL => { //null literal
        builder.advanceLexer()
//        marker.done(ScalaTokenTypes.kNULL)
        marker.drop()
        ScalaElementTypes.LITERAL
      }
      case ScalaTokenTypes.tSTRING_BEGIN => { //String literal
//        val beginMarker = builder.mark();
        builder.advanceLexer()
//        beginMarker.done(ScalaTokenTypes.tSTRING_BEGIN)
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING_END => {
            val strContentMarker = builder.mark()
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
        marker.drop()
        ScalaElementTypes.LITERAL
      }
      case _ => {
        marker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }
  }

}
