package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 15:43:28
* To change this template use File | Settings | File Templates.
*/

/*
 * Path ::= StableId
 *        | [id '.'] 'this'
 */

object Path {
  def parse(builder: PsiBuilder): Boolean = parse(builder,false)
  def parse(builder: PsiBuilder,dot: Boolean): Boolean = {
    val pathMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTHIS => {
                builder.advanceLexer //Ate this
                pathMarker.done(ScalaElementTypes.PATH)
                return true
              }
              case _ => {
                val newMarker = pathMarker.precede
                pathMarker.rollbackTo
                StableId parse builder
                newMarker.done(ScalaElementTypes.PATH)
                return true
              }
            }
          }
          case _ => {
            val newMarker = pathMarker.precede
            pathMarker.rollbackTo
            StableId parse (builder,dot)
            newMarker.done(ScalaElementTypes.PATH)
            return true
          }
        }
      }
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer //Ate this
        pathMarker.done(ScalaElementTypes.PATH)
        return true
      }
      case _ => {
        pathMarker.rollbackTo
        return false
      }
    }
  }
}