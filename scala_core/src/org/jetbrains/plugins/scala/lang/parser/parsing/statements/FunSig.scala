package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import params.{ParamClauses, FunTypeParamClause}
import util.ParserUtils

/** 
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

//TODO: rewrite this
object FunSig {
  def parse(builder: PsiBuilder): Boolean = {
    if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      FunTypeParamClause parse builder
      ParamClauses parse builder
      true
    } else {
      builder error "identifier expected"
      false
    }

  }
}