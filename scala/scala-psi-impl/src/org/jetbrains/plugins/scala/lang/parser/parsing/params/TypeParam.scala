package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Bounds, ContextBounds}
import org.jetbrains.plugins.scala.lang.parser.{ScalaElementType, ScalaParserBundle, ScalaPsiBuilderExt}

/**
 * TypeParam ::= {Annotation} ['+' | '-'] (id | '_') ['^'] [TypeParamClause] Bounds
 *
 * Scala 3.8.0 through 3.10.1 recognize the optional '^' under capture checking.
 * As in the compiler's TypeDef with a CaptureVar attachment, the parameter keeps
 * its ordinary declaration node; the contextual caret distinguishes its syntax.
 * Capture-set bounds are separate CaptureSet children (see [[Bounds]]), not types.
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

    while (Annotation()(using builder)) {
      exist = true
    }

    if (exist) annotationMarker.done(ScalaElementType.ANNOTATIONS)
    else       annotationMarker.drop()


    builder.getTokenText match {
      case "+" | "-" =>
        val varianceMarker = builder.mark()
        builder.advanceLexer()
        if (!mayHaveVariance) varianceMarker.error(ScalaParserBundle.message("variance.annotation.not.allowed"))
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
    if (builder.features.`parses capture checking`) {
      builder.tryParseSoftKeyword(ScalaTokenType.CaptureOperator)
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
        errorMessageBuilder += ScalaParserBundle.message("view.bounds.not.allowed")
      parsedViewBounds = true
    }

    if (ContextBounds()) {
      if (!mayHaveContextBounds)
        errorMessageBuilder += ScalaParserBundle.message("context.bounds.not.allowed")
    }

    val errors = errorMessageBuilder.result()

    if (errors.isEmpty) paramMarker.done(ScalaElementType.TYPE_PARAM)
    else                paramMarker.error(NlsString.force(errors.mkString(";")))

    true
  }
}
