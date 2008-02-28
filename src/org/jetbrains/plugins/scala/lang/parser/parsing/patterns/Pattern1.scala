package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.bnf._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 18:21:08
* To change this template use File | Settings | File Templates.
*/

/*
 * Pattern1 ::= varid ':' TypePat
 *            | '_' ':' TypePat
 *            | Pattern2
 */

object Pattern1 {
  def parse(builder: PsiBuilder): Boolean = {
    val pattern1Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (builder.getTokenText.substring(0, 1).toLowerCase !=
                builder.getTokenText.substring(0, 1)) {
          backupMarker.rollbackTo
        }
        else {
          builder.advanceLexer //Ate id
          builder.getTokenType match {
            case ScalaTokenTypes.tCOLON => {
              builder.advanceLexer //Ate :
              backupMarker.drop
              val typeMarker = builder.mark
              if (!Type.parse(builder)) {
                builder error ScalaBundle.message("wrong.type",new Array[Object](0))
              }
              typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
              pattern1Marker.done(ScalaElementTypes.PATTERN1)
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
            val typeMarker = builder.mark
            if (!Type.parse(builder)) {
              builder error ScalaBundle.message("wrong.type",new Array[Object](0))
            }
            typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
            pattern1Marker.done(ScalaElementTypes.PATTERN1)
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
    if ((new Pattern2).parse(builder) == ScalaElementTypes.WRONGWAY) return false
    else return true
  }
}