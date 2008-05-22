package org.jetbrains.plugins.scala.lang.parser.parsing.params

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint


/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Params ::= Param {',' Param}
 */

object Params {
  def parse(builder: PsiBuilder): Boolean = {
    if (!Param.parse(builder)) {
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate ,
      if (!Param.parse(builder)) {
        builder error ScalaBundle.message("wrong.parameter", new Array[Object](0))
      }
    }
    return true
  }
}