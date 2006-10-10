package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

class Literal {

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()

    builder.getTokenType match{ // Integer literal
      case ScalaTokenTypes.tINTEGER => {
        builder.advanceLexer()
        marker.done(ScalaElementTypes.INTEGER_LITERAL)
      }
      case ScalaTokenTypes.tFLOAT => { //Floating point literal
        builder.advanceLexer()
        marker.done(ScalaElementTypes.FLOATING_POINT_LITERAL)
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kTRUE => { //Boolean Literal
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
        val strMarker = builder.mark();
        val beginMarker = builder.mark();
        builder.advanceLexer()
        beginMarker.done(ScalaElementTypes.STRING_BEGIN)

        val strContentMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING => {
            builder.advanceLexer()
            strContentMarker.done(ScalaElementTypes.STRING_CONTENT)            
          }
          case _ => builder.error("Wrong string literal!")
        }
        val endMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.tSTRING_END => {
            builder.advanceLexer()
            endMarker.done(ScalaElementTypes.STRING_END)
          }
          case _ => builder.error("Wrong string end")
        }
        marker.done(ScalaElementTypes.STRING_LITERAL)
      }
    }
  }

}
