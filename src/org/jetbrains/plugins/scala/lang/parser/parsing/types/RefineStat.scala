package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Dcl
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * RefineStat ::= Dcl
 *              | 'type' TypeDef
 */

object RefineStat {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE => {
        if (!Def.parse(builder,false)){
          Dcl.parse(builder,false)
        }
        return true
      }
      case ScalaTokenTypes.kVAR | ScalaTokenTypes.kVAL
         | ScalaTokenTypes.kDEF => {
        if (Dcl.parse(builder,false)) {
          return true
        }
        else {
          return false
        }
      }
      case _ => {
        return false
      }
    }
  }
}