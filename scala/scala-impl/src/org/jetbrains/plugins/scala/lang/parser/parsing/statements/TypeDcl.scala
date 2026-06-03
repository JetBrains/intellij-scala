package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Bounds, ContextBounds}

/*
 * TypeDcl ::= id [TypeParamClause] ['>:' Type] ['<:' Type] [`:` ContextBounds]
 */
object TypeDcl extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val returnMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE =>
        builder.advanceLexer() //Ate type
      case _ =>
        returnMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
      case _ =>
        builder error ScalaBundle.message("identifier.expected")
        returnMarker.drop()
        return false
    }
    TypeParamClause()
    Bounds.parseSubtypeBounds()

    if (builder.features.`new context bounds and givens`) ContextBounds()

    returnMarker.drop()
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer()
        builder error ScalaBundle.message("wrong.type")
        true
      case _ => true
    }
  }
}