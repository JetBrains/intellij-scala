package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 *  Types ::= Type {',' Type}
 */
object Types extends Types {
  override protected def `type`: ParamType = ParamType
}

trait Types {
  protected def `type`: ParamType

  final def apply(isPattern: Boolean, typeVariables: Boolean)(implicit builder: ScalaPsiBuilder): (Boolean, Boolean) ={
    var isTuple = false

    def parseTypes(): Boolean = {
      if (builder.features.`named tuples` && builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tCOLON)) {
        // Parse named tuple, but only consume tokens for now...
        // Later we want to have special psi elements ala ScNamedTupleElement
        builder.advanceLexer()
        builder.advanceLexer()
        isTuple = true
      }
      if (`type`.parseWithoutScParamTypeCreation(isPattern, typeVariables)(builder)) {
        true
      } else if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
        builder.advanceLexer()
        true
      } else {
        false
      }
    }

    val typesMarker = builder.mark()
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