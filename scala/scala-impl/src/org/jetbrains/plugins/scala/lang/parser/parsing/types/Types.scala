package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
*/

/*
 *  Types ::= Type {',' Type}
 */
object Types extends Types {
  override protected def `type`: ParamType = ParamType
}

trait Types {
  protected def `type`: ParamType

  def parse(builder: ScalaPsiBuilder): (Boolean, Boolean) ={
    var isTuple = false

    def parseTypes() = if (`type`.parseInner(builder)) {
      true
    } else if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer()
      true
    } else {
      false
    }

    val typesMarker = builder.mark
    if (!parseTypes()) {
      typesMarker.drop()
      return (false, isTuple)
    }
    var exit = true
    while (exit && builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
      isTuple = true
      builder.advanceLexer() //Ate ,
      if (!parseTypes()) {
        exit = false
        //builder error ScalaBundle.message("wrong.type",new Array[Object](0))
      }
    }
    if (isTuple) typesMarker.done(ScalaElementType.TYPES)
    else typesMarker.drop()
    (true, isTuple)
  }
}