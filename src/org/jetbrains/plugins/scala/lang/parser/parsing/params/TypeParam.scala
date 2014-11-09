package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

/*
 * TypeParam ::= {Annotation} (id | '_') [TypeParamClause] ['>:' Type] ['<:'Type] {'<%' Type} {':' Type}
 */

object TypeParam {
  def parse(builder: ScalaPsiBuilder, mayHaveVariance: Boolean): Boolean = {
    val paramMarker = builder.mark
    val annotationMarker = builder.mark
    var exist = false
    while (Annotation.parse(builder)) {exist = true}
    if (exist) annotationMarker.done(ScalaElementTypes.ANNOTATIONS)
    else annotationMarker.drop

    if (mayHaveVariance) {
      builder.getTokenText match {
        case "+" | "-" => builder.advanceLexer
        case _ =>
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => {
        TypeParamClause parse builder
      }
      case _ =>
    }

    val boundParser = parseBound(builder) _
    boundParser(">:")
    boundParser("<:")
    while (boundParser("<%")) {}
    while (boundParser(":")) {}

    paramMarker.done(ScalaElementTypes.TYPE_PARAM)
    return true
  }

  def parseBound(builder: ScalaPsiBuilder)(bound: String): Boolean = {
    builder.getTokenText match {
      case x if x == bound => {
        builder.advanceLexer
        if (!Type.parse(builder)) builder error ErrMsg("wrong.type")
        true
      }
      case _ => false
    }
  }
}