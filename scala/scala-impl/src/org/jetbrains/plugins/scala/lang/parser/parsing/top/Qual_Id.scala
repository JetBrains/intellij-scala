package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
  QualId ::= id {. id}
*/

object Qual_Id {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val qualMarker = builder.mark
    return parse(builder,qualMarker)
  }
  def parse(builder: ScalaPsiBuilder, qualMarker: PsiBuilder.Marker): Boolean = {
    //parsing td identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
        //Look for dot
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            val newMarker = qualMarker.precede
            qualMarker.done(ScalaElementType.REFERENCE)
            builder.advanceLexer() //Ate dot
            //recursively parse qualified identifier
            Qual_Id parse(builder, newMarker)
            return true
          }
          case _ => {
            //It's OK, let's close marker
            qualMarker.done(ScalaElementType.REFERENCE)
            return true
          }
        }
      case _ =>
        builder error ScalaBundle.message("wrong.qual.identifier")
        qualMarker.drop()
        return true
    }
  }
}