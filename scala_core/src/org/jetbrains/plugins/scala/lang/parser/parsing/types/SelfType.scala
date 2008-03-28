package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * SelfType ::= id [':' Type] '=>' |
 *              'this' ':' Type '=>'
 */

object SelfType {
  def parse(builder: PsiBuilder) {
    val selfTypeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer // Ate this
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate ':'
            if (!TypePattern.parse(builder)) {
             selfTypeMarker.rollbackTo
              return
            }
            else {
              builder.getTokenType match {
                case ScalaTokenTypes.tFUNTYPE => {
                  builder.advanceLexer //Ate '=>'
                  selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
                  return
                }
                case _ => {
                  selfTypeMarker.rollbackTo
                  return
                }
              }
            }
          }
          case _ => {
            selfTypeMarker.rollbackTo
            return
          }
        }
      }
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate ':'
            if (!TypePattern.parse(builder)) {
             selfTypeMarker.rollbackTo
              return
            }
            else {
              builder.getTokenType match {
                case ScalaTokenTypes.tFUNTYPE => {
                  builder.advanceLexer //Ate '=>'
                  selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
                  return
                }
                case _ => {
                  selfTypeMarker.rollbackTo
                  return
                }
              }
            }
          }
          case ScalaTokenTypes.tFUNTYPE => {
            builder.advanceLexer //Ate '=>'
            selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
            return
          }
          case _ => {
            selfTypeMarker.rollbackTo
            return
          }
        }
      }
      case _ => {
        selfTypeMarker.rollbackTo
        return
      }
    }
  }
}