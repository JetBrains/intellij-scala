package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParam
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{AnnotType, SimpleType}

/*
 * Constr ::= AnnotType {ArgumentExprs}
 */
object Constructor extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = parse(isAnnotation = false, stopBeforeNonEmptyParameterClause = false)

  def parse(isAnnotation: Boolean, stopBeforeNonEmptyParameterClause: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val constrMarker = builder.mark()
    val latestDoneMarker = builder.getLatestDoneMarker

    val annotationAllowed = latestDoneMarker == null ||
      (latestDoneMarker.getTokenType != ScalaElementType.TYPE_GENERIC_CALL &&
        latestDoneMarker.getTokenType != ScalaElementType.MODIFIERS &&
        latestDoneMarker.getTokenType != ScalaElementType.TYPE_PARAM_CLAUSE)

    val parsedType =
      if (isAnnotation) {
        SimpleType(isPattern = false)
      } else {
        AnnotType(isPattern = false, multipleSQBrackets = false)
      }

    if (!parsedType) {
      constrMarker.drop()
      return false
    }

    if (builder.isScala3) {
      var first = true

      while (
        builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS &&
          (!stopBeforeNonEmptyParameterClause || !isParameter(first)) &&
          ArgumentExprs()
      ) {
        first = false
      }

    } else {
      if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
        if (ArgumentExprs()) {
          while (
            builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS &&
              (!isAnnotation || annotationAllowed) &&
              ArgumentExprs()
          ) {
            // already parsed ArgumentExprs
          }
        }
      }
    }

    constrMarker.done(ScalaElementType.CONSTRUCTOR)
    true
  }

  private def isParameter(first: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = builder.predict { builder =>
    builder.getTokenType == ScalaTokenTypes.kIMPLICIT ||
      builder.getTokenText == "using" ||
      (!first && builder.getTokenType == ScalaTokenTypes.tRPARENTHESIS) ||
      ClassParam.parse(ignoreErrors = false)(builder)
  }
}