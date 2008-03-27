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
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 16:25:10
* To change this template use File | Settings | File Templates.
*/

/*
 * Params ::= Param {',' Param}
 */

object Params {
  def parse(builder: PsiBuilder): Boolean = {
    val paramsMarker = builder.mark
    if (!Param.parse(builder)) {
      paramsMarker.drop
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate ,
      if (!Param.parse(builder)) {
        builder error ScalaBundle.message("wrong.parameter", new Array[Object](0))
      }
    }
    paramsMarker.done(ScalaElementTypes.PARAMS)
    return true
  }
}