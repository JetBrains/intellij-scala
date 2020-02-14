package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Bounds, Type}

/*
 * TypeParam ::= {Annotation} (id | '_') [TypeParamClause] ['>:' Type] ['<:'Type] {'<%' Type} {':' Type}
 */
object TypeParam {

  def parse(
    builder:              ScalaPsiBuilder,
    mayHaveVariance:      Boolean = true,
    mayHaveViewBounds:    Boolean = true,
    mayHaveContextBounds: Boolean = true
  ): Boolean = {
    implicit val b: ScalaPsiBuilder = builder

    val paramMarker = builder.mark
    val annotationMarker = builder.mark
    var exist = false

    while (Annotation.parse(builder)) {
      exist = true
    }

    if (exist) annotationMarker.done(ScalaElementType.ANNOTATIONS)
    else annotationMarker.drop()

    if (mayHaveVariance) {
      builder.getTokenText match {
        case "+" | "-" => builder.advanceLexer()
        case _         =>
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate identifier
      case _ =>
        paramMarker.rollbackTo()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        TypeParamClause parse builder
      case _ =>
    }

    Bounds.parse(Bounds.LOWER)
    Bounds.parse(Bounds.UPPER)

    if (mayHaveViewBounds)    while (Bounds.parse(Bounds.VIEW)) {}
    if (mayHaveContextBounds) while (Bounds.parse(Bounds.CONTEXT)) {}

    paramMarker.done(ScalaElementType.TYPE_PARAM)
    true
  }
}