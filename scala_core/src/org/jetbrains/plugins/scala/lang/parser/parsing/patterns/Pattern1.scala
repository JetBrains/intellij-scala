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
              if (!TypePattern.parse(builder)) {
                builder error ScalaBundle.message("wrong.type",new Array[Object](0))
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
              builder error ScalaBundle.message("wrong.type",new Array[Object](0))
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
    if (!Pattern2.parse(builder)) return false
    else return true
  }
}