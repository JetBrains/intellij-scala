package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

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
  def parse(builder: PsiBuilder): Boolean = {

    def isVarId = {
      val text = builder.getTokenText
      text.substring(0, 1).toLowerCase != text.substring(0, 1) || (
              text.apply(0) == '`' && text.apply(text.length - 1) == '`'
              )
    }

    val pattern1Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (isVarId) {
          backupMarker.rollbackTo
        }
        else {
          builder.advanceLexer //Ate id
          builder.getTokenType match {
            case ScalaTokenTypes.tCOLON => {
              builder.advanceLexer //Ate :
              backupMarker.drop
              if (!TypePattern.parse(builder)) {
                builder error ScalaBundle.message("wrong.type")
              }
              pattern1Marker.done(ScalaElementTypes.TYPED_PATTERN)
              return true
            }

            case _ => {
              backupMarker.rollbackTo
            }
          }
        }
      }
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate _
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
            backupMarker.drop
            if (!TypePattern.parse(builder)) {
              builder error ScalaBundle.message("wrong.type")
            }
            pattern1Marker.done(ScalaElementTypes.TYPED_PATTERN)
            return true
          }
          case _ => {
            backupMarker.rollbackTo
          }
        }
      }
      case _ => {
        backupMarker.drop
      }
    }
    pattern1Marker.drop
    Pattern2.parse(builder, false)
  }
}