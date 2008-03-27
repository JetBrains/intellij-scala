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
* Time: 16:04:25
* To change this template use File | Settings | File Templates.
*/

/*
 * FunTypeParam ::= '[' TypeParam {',' TypeParam} ']'
 */

object FunTypeParamClause {
  def parse(builder: PsiBuilder): Boolean = {
    val funMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => builder.advanceLexer //Ate [
      case _ => {
        funMarker.drop
        return false
      }
    }
    if (!VariantTypeParam.parse(builder)) {
      builder error ErrMsg("wrong.parameter")
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate
      if (!TypeParam.parse(builder)) {
        builder error ErrMsg("wrong.parameter")
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRSQBRACKET => {
        builder.advanceLexer //Ate ]
      }
      case _ => builder error ErrMsg("wrong.parameter")
    }
    funMarker.done(ScalaElementTypes.TYPE_PARAM_CLAUSE)
    return true
  }
}