package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 08.02.2008
* Time: 12:40:41
* To change this template use File | Settings | File Templates.
*/

/*
 * ParamType ::= Type |
 *               '=>' Type |
 *               Type '*'
 */

object ParamType {
  def parse(builder: PsiBuilder): Boolean = {
    val paramTypeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate '=>'
        if (!Type.parse(builder)) {
          paramTypeMarker.rollbackTo
          return false
        }
        else {
          paramTypeMarker.done(ScalaElementTypes.PARAM_TYPE)
          return true
        }
      }
      case _ => {
        if (!Type.parse(builder)) {
          paramTypeMarker.rollbackTo
          return false
        }
        else {
          builder.getTokenType match {
            case ScalaTokenTypes.tSTAR => builder.advanceLexer // Ate '*'
            case _ => {/*nothing needs to do*/}
          }
          paramTypeMarker.done(ScalaElementTypes.PARAM_TYPE)
          return true
        }
      }
    }
  }
}