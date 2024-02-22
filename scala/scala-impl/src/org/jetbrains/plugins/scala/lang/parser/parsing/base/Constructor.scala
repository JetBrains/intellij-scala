package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{AnnotType, SimpleType}

/*
 * Constr ::= AnnotType {ArgumentExprs}
 */
object Constructor extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = parse(isAnnotation = false)

  def parse(isAnnotation: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val constrMarker = builder.mark()
    val latestDoneMarker = builder.getLatestDoneMarker

    val annotationAllowed = latestDoneMarker == null ||
      (latestDoneMarker.getTokenType != ScalaElementType.TYPE_GENERIC_CALL &&
        latestDoneMarker.getTokenType != ScalaElementType.MODIFIERS &&
        latestDoneMarker.getTokenType != ScalaElementType.TYPE_PARAM_CLAUSE)

    if ((!isAnnotation && !AnnotType(isPattern = false, multipleSQBrackets = false)) ||
      (isAnnotation && !SimpleType(isPattern = false))) {
      constrMarker.drop()
      return false
    }

    if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      if (ArgumentExprs())

      while (
        builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS &&
          (!isAnnotation || annotationAllowed) &&
          ArgumentExprs()
      ) {
        // already parsed ArgumentExprs
      }
    }

    constrMarker.done(ScalaElementType.CONSTRUCTOR)
    true
  }
}