package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType

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

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()

    builder.getTokenType match{
      case ScalaTokenTypes.tINTEGER => { // Integer literal
        builder.advanceLexer()
        marker.done(ScalaElementTypes.INTEGER_LITERAL)
      }
      case ScalaTokenTypes.tFLOAT => { //Floating point literal
        builder.advanceLexer()
        marker.done(ScalaElementTypes.FLOATING_POINT_LITERAL)
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => { //Boolean Literal
        val boolMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.kTRUE => boolMarker.done(ScalaElementTypes.TRUE)
          case ScalaTokenTypes.kFALSE => boolMarker.done(ScalaElementTypes.FALSE)
        }
        builder.advanceLexer()
        marker.done(ScalaElementTypes.BOOLEAN_LITERAL)
      }
      case ScalaTokenTypes.tCHAR => { //Character literal
        builder.advanceLexer()
        marker.done(ScalaElementTypes.CHARACTER_LITERAL)
      }
      case ScalaTokenTypes.kNULL => { //null literal
        builder.advanceLexer()
        marker.done(ScalaElementTypes.NULL)
      }


      case ScalaTokenTypes.tSTRING_BEGIN => { //String literal
        val beginMarker = builder.mark();
        builder.advanceLexer()
        beginMarker.done(ScalaElementTypes.STRING_BEGIN)
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING_END => {
            val strContentMarker = builder.mark()
            strContentMarker.done(ScalaElementTypes.STRING_CONTENT)
            val endMarker = builder.mark()
            builder.advanceLexer()
            endMarker.done(ScalaElementTypes.STRING_END)
          }
          case ScalaTokenTypes.tSTRING => {
            val strContentMarker = builder.mark()
            builder.advanceLexer()
            strContentMarker.done(ScalaElementTypes.STRING_CONTENT)
            builder.getTokenType match {
              case ScalaTokenTypes.tSTRING_END => {
                val endMarker = builder.mark()
                builder.advanceLexer()
                endMarker.done(ScalaElementTypes.STRING_END)
              }
              case _ => builder.error("Wrong string completion")
            }
          }
          case _ => builder.error("Wrong string declaration")
        }
        marker.done(ScalaElementTypes.STRING_LITERAL)
      }
    }
  }

}
