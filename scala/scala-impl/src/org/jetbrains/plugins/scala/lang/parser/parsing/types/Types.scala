package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
*/

/*
 *  Types ::= Type {',' Type}
 */
object Types extends Types {
  override protected def `type` = ParamType
}

trait Types extends ParserNode {
  protected def `type`: ParamType

  def parse(builder: ScalaPsiBuilder): (Boolean, Boolean) ={
    var isTuple = false

    def typesParse() = if (`type`.parseInner(builder)) {
      true
    } else if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer()
      true
    } else {
      false
    }

    val typesMarker = builder.mark
    if (!typesParse) {
      typesMarker.drop()
      return (false, isTuple)
    }
    var exit = true
    while (exit && builder.getTokenType == ScalaTokenTypes.tCOMMA && !ParserUtils.eatTrailingComma(builder, ScalaTokenTypes.tRPARENTHESIS)) {
      isTuple = true
      builder.advanceLexer() //Ate ,
      if (!typesParse) {
        exit = false
        //builder error ScalaBundle.message("wrong.type",new Array[Object](0))
      }
    }
    if (isTuple) typesMarker.done(ScalaElementTypes.TYPES)
    else typesMarker.drop()
    (true, isTuple)
  }
}