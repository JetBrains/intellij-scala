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
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Dcl
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 12:36:41
* To change this template use File | Settings | File Templates.
*/

/*
 * RefineStat ::= Dcl
 *              | 'type' TypeDef
 */

object RefineStat {
  def parse(builder: PsiBuilder): Boolean = {
    val refineStatMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE => {
        if (!Def.parse(builder,false)){
          Dcl.parse(builder,false)
        }
        refineStatMarker.done(ScalaElementTypes.REFINE_STAT)
        return true
      }
      case ScalaTokenTypes.kVAR | ScalaTokenTypes.kVAL
         | ScalaTokenTypes.kDEF => {
        if (Dcl.parse(builder,false)) {
          refineStatMarker.done(ScalaElementTypes.REFINE_STAT)
          return true
        }
        else {
          refineStatMarker.drop
          return false
        }
      }
      case _ => {
        refineStatMarker.drop
        return false
      }
    }
  }
}