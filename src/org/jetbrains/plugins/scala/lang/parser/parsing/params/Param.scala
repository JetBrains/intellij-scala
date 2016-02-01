package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Param ::= {Annotation} id [':' ParamType] ['=' Expr]
 */
object Param extends Param {
  override protected val expr = Expr
  override protected val annotation = Annotation
  override protected val paramType = ParamType
}

trait Param {
  protected val expr: Expr
  protected val annotation: Annotation
  protected val paramType: ParamType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark
    //parse annotations
    val annotationsMarker = builder.mark
    while (annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)

    //empty modifiers
    val modifiersMarker = builder.mark()
    modifiersMarker.done(ScalaElementTypes.MODIFIERS)

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate id
      case _ =>
        paramMarker.rollbackTo()
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
        if (!paramType.parse(builder)) builder error ErrMsg("wrong.type")
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate =
        if (!expr.parse(builder)) builder error ErrMsg("wrong.expression")
      case _ =>
    }
    paramMarker.done(ScalaElementTypes.PARAM)
    true
  }
}