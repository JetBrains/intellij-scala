package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

/*
  QualId ::= id {. id}
*/

object QualId extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    parseNext(builder.mark(), hadOneRef = false)
  }

  @tailrec
  private def parseNext(qualMarker: PsiBuilder.Marker, hadOneRef: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    //parsing td identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
        //Look for dot
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT =>
            val newMarker = qualMarker.precede()
            qualMarker.done(ScalaElementType.REFERENCE)
            builder.advanceLexer() //Ate dot
            //recursively parse qualified identifier
            parseNext(newMarker, hadOneRef = true)
          case _ =>
            //It's OK, let's close marker
            qualMarker.done(ScalaElementType.REFERENCE)
            true
        }
      case _ =>
        builder error ScalaBundle.message("wrong.qual.identifier")
        qualMarker.drop()
        hadOneRef
    }
  }
}