package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * AnnotationExpr ::= Constr [[nl] '{' {NameValuePair} '}']
 */
object AnnotationExpr {
  def apply(forConstructor: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val annotExprMarker = builder.mark()
    if (Constructor.parse(isAnnotation = true, stopBeforeNonEmptyParameterClause = forConstructor)) {
      annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
      true
    } else {
      annotExprMarker.drop()
      false
    }
  }
}