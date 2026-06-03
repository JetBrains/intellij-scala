package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Bounds, ContextBounds}
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

/*
 * TypeParam ::= {Annotation} (id | '_') [TypeParamClause] ['>:' Type] ['<:'Type] {'<%' Type} {':' Type}
 */
object TypeParam {

  def apply(
    mayHaveVariance:      Boolean = true,
    mayHaveViewBounds:    Boolean = true,
    mayHaveContextBounds: Boolean = true
  )(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker         = builder.mark()
    val annotationMarker    = builder.mark()
    val errorMessageBuilder = List.newBuilder[String]
    var exist               = false

    while (Annotation()(builder)) {
      exist = true
    }

    if (exist) annotationMarker.done(ScalaElementType.ANNOTATIONS)
    else       annotationMarker.drop()


    builder.getTokenText match {
      case "+" | "-" =>
        val varianceMarker = builder.mark()
        builder.advanceLexer()
        if (!mayHaveVariance) varianceMarker.error(ScalaBundle.message("variance.annotation.not.allowed"))
        else                  varianceMarker.drop()
      case _ =>
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
        TypeParamClause()
      case _ =>
    }

    Bounds(Bounds.LOWER)
    Bounds(Bounds.UPPER)

    var parsedViewBounds    = false

    while (Bounds(Bounds.VIEW)) {
      if (!parsedViewBounds && !mayHaveViewBounds)
        errorMessageBuilder += ScalaBundle.message("view.bounds.not.allowed")
      parsedViewBounds = true
    }

    if (ContextBounds()) {
      if (!mayHaveContextBounds)
        errorMessageBuilder += ScalaBundle.message("context.bounds.not.allowed")
    }

    val errors = errorMessageBuilder.result()

    if (errors.isEmpty) paramMarker.done(ScalaElementType.TYPE_PARAM)
    else                paramMarker.error(NlsString.force(errors.mkString(";")))

    true
  }
}