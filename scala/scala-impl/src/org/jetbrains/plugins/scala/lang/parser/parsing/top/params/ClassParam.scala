package org.jetbrains.plugins.scala.lang.parser.parsing.top
package params

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Annotations, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
 * [[ClassParam]] ::= [[Annotations]] [{Modifier} ('val' | 'var')] id ':' ParamType ['=' Expr]
*/
object ClassParam extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = parse(ignoreErrors = true)

  def parse(ignoreErrors: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark()

    Annotations()
    var hasError = false

    //parse modifiers
    val modifierMarker = builder.mark()
    var isModifier = false
    while (Modifier()) {
      isModifier = true
    }
    if (builder.isScala3 && canFollowInlineKeyword(builder.lookAhead(1))) {
      builder.tryParseSoftKeyword(ScalaTokenType.InlineKeyword)
    }
    modifierMarker.done(ScalaElementType.MODIFIERS)

    //Look for var or val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAR |
           ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Let's ate this!
      case _ =>
        if (isModifier) {
          builder error ScalaBundle.message("val.var.expected")
        }
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
      case _ =>
        classParamMarker.rollbackTo()
        return false
    }
    //Try to parse tale
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate ':'
        if (!ParamType()) {
          builder.error(ScalaBundle.message("parameter.type.expected"))
          hasError = true
        }
      case _ =>
        builder.error(ScalaBundle.message("colon.expected"))
        hasError = true
    }

    //default param
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate '='
        if (!Expr()) {
          builder.wrongExpressionError()
          hasError = true
        }
      case _ =>
    }
    classParamMarker.done(ScalaElementType.CLASS_PARAM)
    ignoreErrors || !hasError
  }

  private val canFollowInlineKeyword = Set(
    ScalaTokenTypes.kVAL,
    ScalaTokenTypes.kVAR,
    ScalaTokenTypes.tIDENTIFIER
  )
}