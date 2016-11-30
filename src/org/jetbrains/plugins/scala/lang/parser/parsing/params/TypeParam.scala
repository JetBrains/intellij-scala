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
object TypeParam extends TypeParam {
  override protected def annotation = Annotation
  override protected def `type` = Type
  override protected def typeParamClause = TypeParamClause
}

trait TypeParam {
  protected def annotation: Annotation
  protected def `type`: Type
  protected def typeParamClause: TypeParamClause

  def parse(builder: ScalaPsiBuilder, mayHaveVariance: Boolean): Boolean = {
    val paramMarker = builder.mark
    val annotationMarker = builder.mark
    var exist = false
    while (annotation.parse(builder)) {
      exist = true
    }
    if (exist) annotationMarker.done(ScalaElementTypes.ANNOTATIONS)
    else annotationMarker.drop()

    if (mayHaveVariance) {
      builder.getTokenText match {
        case "+" | "-" => builder.advanceLexer()
        case _ =>
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        builder.advanceLexer //Ate identifier
      case _ =>
        paramMarker.rollbackTo
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        typeParamClause parse builder
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
      case x if x == bound =>
        builder.advanceLexer
        if (!`type`.parse(builder)) builder error ErrMsg("wrong.type")
        true
      case _ => false
    }
  }
}