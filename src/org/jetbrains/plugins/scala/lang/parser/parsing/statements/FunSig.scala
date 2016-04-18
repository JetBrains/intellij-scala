package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{FunTypeParamClause, ParamClauses}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/** 
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/
object FunSig extends FunSig {
  override protected val paramClauses = ParamClauses
}

//TODO: rewrite this
trait FunSig {
  protected val paramClauses: ParamClauses

  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      FunTypeParamClause parse builder
      paramClauses parse builder
      true
    } else {
      builder error "identifier expected"
      false
    }

  }
}