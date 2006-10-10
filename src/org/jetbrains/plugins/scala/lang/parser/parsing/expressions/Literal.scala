package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

object Literal {

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

        val strContentMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING => builder.advanceLexer()
          case _ => builder.error("Wrong string literal!")
        }
        strContentMarker.done(ScalaElementTypes.STRING_CONTENT)
        val endMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING_END => builder.advanceLexer()
          case _ => builder.error("Wrong string end")
        }
        endMarker.done(ScalaElementTypes.STRING_END)
        marker.done(ScalaElementTypes.STRING_LITERAL)
      }
    }
  }

}
