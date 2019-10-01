package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotations, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
 * [[Param]] ::= [[Annotations]] id [':' ParamType] ['=' Expr]
 *
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
object Param {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark

    Annotations()(builder)

    //empty modifiers
    val modifiersMarker = builder.mark()
    modifiersMarker.done(ScalaElementType.MODIFIERS)

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
        if (!ParamType.parse(builder)) builder error ErrMsg("wrong.type")
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate =
        if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
      case _ =>
    }
    paramMarker.done(ScalaElementType.PARAM)
    true
  }
}