package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotations, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
 * [[Param]] ::= [[Annotations]] id ':' ParamType ['=' Expr]
 */
object Param extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark()

    Annotations()

    val modifiersMarker = builder.mark()
    if (builder.isScala3 && builder.lookAhead(1, ScalaTokenTypes.tIDENTIFIER)) {
      builder.tryParseSoftKeyword(ScalaTokenType.InlineKeyword)
    }
    modifiersMarker.done(ScalaElementType.MODIFIERS)

    if (builder.getTokenType == ScalaTokenTypes.kVAL || builder.getTokenType == ScalaTokenTypes.kVAR) {
      // val and var are not allowed here, but to get better parsing experience and error reporting
      // we parse them nonetheless here and report them in ScParameterAnnotator
      builder.advanceLexer() //Ate val or var
    }

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
      case _ =>
        builder error ErrMsg("colon.expected")
    }

    if (!ParamType()) builder error ErrMsg("parameter.type.expected")

    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate =
        if (!Expr()) builder error ErrMsg("expression.expected")
      case _ =>
    }
    paramMarker.done(ScalaElementType.PARAM)
    true
  }
}