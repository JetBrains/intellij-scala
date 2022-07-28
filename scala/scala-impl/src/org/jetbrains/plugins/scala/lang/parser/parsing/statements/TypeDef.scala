package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Bounds, Type}

object TypeDef extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val faultMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE =>
        builder.advanceLexer() //Ate type
      case _ =>
        faultMarker.rollbackTo()
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
      case _ =>
        builder.error(ScalaBundle.message("identifier.expected"))
        faultMarker.rollbackTo()
        return false
    }
    TypeParamClause()

    if (builder.isScala3) {
      Bounds.parseSubtypeBounds()
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate =
        if (Type()) {
          faultMarker.drop()
          true
        }
        else {
          faultMarker.drop()
          builder.error(ScalaBundle.message("wrong.type"))
          false
        }
      case _ =>
        faultMarker.rollbackTo()
        false
    }
  }
}
