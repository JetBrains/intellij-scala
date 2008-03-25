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
* Date: 29.02.2008
* Time: 12:31:03
* To change this template use File | Settings | File Templates.
*/

/*
 * Pattern2 ::= varid '@' Pattern3
 *            | Pattern3
 */

object Pattern2 {
  def parse(builder: PsiBuilder): Boolean = {
    val pattern2Marker = builder.mark
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
            case ScalaTokenTypes.tAT => {
              builder.advanceLexer //Ate @
              backupMarker.drop
              if (!Pattern3.parse(builder)) {
                builder error ScalaBundle.message("wrong.pattern", new Array[Object](0))
              }
              pattern2Marker.done(ScalaElementTypes.BINDING_PATTERN)
              return true
            }
            case _ => {
              backupMarker.rollbackTo
            }
          }
        }
      }
      case _ => {
        backupMarker.drop
      }
    }
    pattern2Marker.drop
    if (Pattern3.parse(builder)) return true
    else return false
  }
}