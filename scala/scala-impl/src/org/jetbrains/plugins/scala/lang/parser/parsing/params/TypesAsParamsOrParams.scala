package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

abstract class TypesAsParams(val paramType: IElementType) extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3)

    val rollbackMarker = builder.mark()

    if (!tryParseTypeAsParam(rollback = true)) {
      rollbackMarker.rollbackTo()
      return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA =>
      case ScalaTokenTypes.tRPARENTHESIS =>
      case _ =>
        rollbackMarker.rollbackTo()
        return false
    }

    rollbackMarker.drop()

    while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
      builder.advanceLexer() // ate ,

      if (!tryParseTypeAsParam(rollback = false)) {
        builder error ErrMsg("expected.more.types")
        return false
      }
    }

    true
  }

  private def tryParseTypeAsParam(rollback: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark()
    val typeMarker = builder.mark()
    if (Type.parse(builder)) {
      typeMarker.done(ScalaElementType.PARAM_TYPE)
      paramMarker.done(paramType)
      true
    } else {
      typeMarker.drop()
      if (rollback) paramMarker.rollbackTo()
      else paramMarker.drop()
      false
    }
  }
}

object TypesAsParams extends TypesAsParams(ScalaElementType.PARAM)

object TypesAsClassParams extends TypesAsParams(ScalaElementType.CLASS_PARAM)
