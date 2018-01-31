package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Pattern1 ::= varid ':' TypePat
 *            | '_' ':' TypePat
 *            | Pattern2
 */
object Pattern1 extends Pattern1 {
  override protected def pattern2 = Pattern2
  override protected def typePattern = TypePattern
}

trait Pattern1 {
  protected def pattern2: Pattern2
  protected def typePattern: TypePattern

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val pattern1Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        if (ParserUtils.isCurrentVarId(builder) && !ParserUtils.isIdBindingEnabled(builder)) {
          backupMarker.rollbackTo()
        } else {
          builder.advanceLexer() //Ate id
          builder.getTokenType match {
            case ScalaTokenTypes.tCOLON =>
              builder.advanceLexer() //Ate :
              backupMarker.drop()
              if (!typePattern.parse(builder)) {
                builder error ScalaBundle.message("wrong.type")
              }
              pattern1Marker.done(ScalaElementTypes.TYPED_PATTERN)
              return true

            case _ =>
              backupMarker.rollbackTo()
          }
        }
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate _
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON =>
            builder.advanceLexer() //Ate :
            backupMarker.drop()
            if (!typePattern.parse(builder)) {
              builder error ScalaBundle.message("wrong.type")
            }
            pattern1Marker.done(ScalaElementTypes.TYPED_PATTERN)
            return true
          case _ =>
            backupMarker.rollbackTo()
        }
      case _ =>
        backupMarker.drop()
    }
    pattern1Marker.drop()
    pattern2.parse(builder, forDef = false)
  }
}