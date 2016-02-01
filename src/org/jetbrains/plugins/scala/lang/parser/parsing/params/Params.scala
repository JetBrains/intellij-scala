package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Params ::= Param {',' Param}
 */
object Params extends Params {
  override protected val param = Param
}

trait Params {
  protected val param: Param

  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (!param.parse(builder)) {
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() //Ate ,
      if (!param.parse(builder)) {
        builder error ScalaBundle.message("wrong.parameter")
      }
    }
    true
  }
}