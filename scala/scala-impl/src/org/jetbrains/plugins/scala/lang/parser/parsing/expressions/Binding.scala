package org.jetbrains.plugins.scala.lang.parser.parsing.expressions


import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
 * Binding ::= [erased] (id | '_') [':' Type]
 */
object Binding extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark()
    builder.mark().done(ScalaElementType.ANNOTATIONS)

    val modifierMarker = builder.mark()
    val hasErasedModifier = builder.isScala3 &&
      builder.lookAhead(1, ScalaTokenTypes.tIDENTIFIER) &&
      builder.tryParseSoftKeyword(ScalaTokenType.ErasedKeyword)
    if (hasErasedModifier) modifierMarker.done(ScalaElementType.MODIFIERS)
    else modifierMarker.drop()

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        builder.advanceLexer()
      case _ =>
        paramMarker.rollbackTo()
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
        if (!ParamType()) builder error ErrMsg("wrong.type")
      case _ =>
    }

    paramMarker.done(ScalaElementType.PARAM)
    true
  }
}
