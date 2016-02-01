package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/
object TypeDef extends TypeDef {
  override protected val `type` = Type
  override protected val typeParamClause = TypeParamClause
}

trait TypeDef {
  protected val `type`: Type
  protected val typeParamClause: TypeParamClause

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val faultMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE =>
        builder.advanceLexer //Ate type
      case _ =>
        faultMarker.rollbackTo
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer //Ate identifier
      case _ =>
        builder error ScalaBundle.message("identifier.expected")
        faultMarker.rollbackTo
        return false
    }
    val isTypeParamClause = if (typeParamClause parse builder) {
      true
    } else false
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer //Ate =
        if (`type`.parse(builder)) {
          faultMarker.drop
          return true
        }
        else {
          faultMarker.drop
          builder error ScalaBundle.message("wrong.type")
          return false
        }
      case _ =>
        faultMarker.rollbackTo
        return false
    }
  }
}