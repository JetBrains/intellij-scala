package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

/*
  QualId ::= id {. id}
*/

object QualId extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    parseNext(builder.mark())
    true
  }

  @tailrec
  private def parseNext(qualMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
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
            parseNext(newMarker)
          }
          case _ =>
            //It's OK, let's close marker
            qualMarker.done(ScalaElementType.REFERENCE)
        }
      case _ =>
        builder error ScalaBundle.message("wrong.qual.identifier")
        qualMarker.drop()
    }
  }
}