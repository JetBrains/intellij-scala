package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

object QualId{
/*
  def parse(builder : PsiBuilder, marker : PsiBuilder.Marker ) : Unit = {

    builder.getTokenType match {
      case ScalaTokenTypes.tDOT => {

        val preMarker = marker.precede()
        marker.done(ScalaElementTypes.QUAL_ID)

        val dotMarker = builder.mark();
        builder.advanceLexer // Ate dot
        dotMarker.done(ScalaElementTypes.DOT)

        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {

            val idMarker = builder.mark()
            builder.advanceLexer
            idMarker.done(ScalaElementTypes.IDENTIFIER)

            (new QualId).parse(builder,preMarker)
          }
          case _ => {
            builder.error("Wrong package name declaration")
            preMarker.done(ScalaElementTypes.QUAL_ID)
          }
        }
      }

      case ScalaTokenTypes.tLINE_TERMINATOR => { //End of package
        marker.done(ScalaElementTypes.QUAL_ID)
        builder.advanceLexer
      }
      case ScalaTokenTypes.tSEMICOLON => { //End of package
        marker.done(ScalaElementTypes.QUAL_ID)
        val semMarker = builder.mark()
        builder.advanceLexer
        semMarker.done(ScalaElementTypes.SEMICOLON)
      }
      case _ => {
        builder.error("Wrong package name declaration")
        marker.done(ScalaElementTypes.QUAL_ID)
      }
    }


  }
*/
}
