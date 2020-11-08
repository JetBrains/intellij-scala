package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Bounds, MatchType, Type}

/**
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/
object TypeDef extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val faultMarker = builder.mark
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
    TypeParamClause.parse(builder)

    if (builder.isScala3) {
      Bounds.parseSubtypeBounds()
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate =
        if (Type.parse(builder)) {
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
