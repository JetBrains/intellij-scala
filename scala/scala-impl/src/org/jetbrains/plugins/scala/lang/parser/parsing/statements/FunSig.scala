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
//TODO: rewrite this
object FunSig {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
      builder.checkedAdvanceLexer()
      FunTypeParamClause parse builder
      ParamClauses parse builder
      true
    } else {
      builder error ScalaBundle.message("identifier.expected")
      false
    }

  }
}