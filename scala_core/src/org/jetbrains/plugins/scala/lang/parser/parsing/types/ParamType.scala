package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import util.ParserUtils._
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ParamType ::= Type |
 *               '=>' Type |
 *               Type '*'
 */

object ParamType {
  def parseInner(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate '=>'
          Type.parse(builder)
      }
      case _ => {
        if (!Type.parse(builder,true)) false else {
          builder.getTokenText match {
            case "*" => builder.advanceLexer // Ate '*'
            case _ => {/* nothing needs to be done */}
          }
          true
        }
      }
    }
  }

  def parse(builder : PsiBuilder) = build(ScalaElementTypes.PARAM_TYPE, builder) { parseInner(builder) }
}