package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Bounds

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

    val paramMarker         = builder.mark
    val annotationMarker    = builder.mark
    val varianceMarker      = builder.mark()
    val errorMessageBuilder = List.newBuilder[String]
    var exist               = false

    while (Annotation.parse(builder)) {
      exist = true
    }

    if (exist) annotationMarker.done(ScalaElementType.ANNOTATIONS)
    else       annotationMarker.drop()


    builder.getTokenText match {
      case "+" | "-" =>
        builder.advanceLexer()
        if (!mayHaveVariance)
          varianceMarker.error(ScalaBundle.message("variance.annotation.not.allowed"))
      case _ => varianceMarker.drop()
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

    var parsedViewBounds    = false
    var parsedContextBounds = false

    while (Bounds.parse(Bounds.VIEW)) {
      if (!parsedViewBounds && !mayHaveViewBounds)
        errorMessageBuilder += ScalaBundle.message("view.bounds.not.allowed")
      parsedViewBounds = true
    }

    while (Bounds.parse(Bounds.CONTEXT)) {
      if (!parsedContextBounds && !mayHaveContextBounds)
        errorMessageBuilder += ScalaBundle.message("context.bounds.not.allowed")
      parsedContextBounds = true
    }

    val errors = errorMessageBuilder.result()

    if (true) paramMarker.done(ScalaElementType.TYPE_PARAM)
    else                paramMarker.error(errors.mkString(";"))
    true
  }
}