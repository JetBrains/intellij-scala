package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Pattern1 ::= varid ':' TypePat
 *            | '_' ':' TypePat
 *            | Pattern2
 */
object Pattern1 {

  //TODO: refactor!
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val pattern1Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        if (builder.isIdBinding) {
          builder.advanceLexer() //Ate id
          builder.getTokenType match {
            case ScalaTokenTypes.tCOLON =>
              builder.advanceLexer() //Ate :
              backupMarker.drop()
              if (!TypePattern.parse(builder)) {
                builder error ScalaBundle.message("wrong.type")
              }
              pattern1Marker.done(ScalaElementType.TYPED_PATTERN)
              return true
            case _ =>
              backupMarker.rollbackTo()
          }
        } else {
          backupMarker.rollbackTo()
        }
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate _
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON =>
            builder.advanceLexer() //Ate :
            backupMarker.drop()
            if (!TypePattern.parse(builder)) {
              builder error ScalaBundle.message("wrong.type")
            }
            pattern1Marker.done(ScalaElementType.TYPED_PATTERN)
            return true
          case _ =>
            backupMarker.rollbackTo()
        }
      case _ =>
        backupMarker.drop()
    }
    pattern1Marker.drop()
    Pattern2.parse(builder, forDef = false)
  }
}