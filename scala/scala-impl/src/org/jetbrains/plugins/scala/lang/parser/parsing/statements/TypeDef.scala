package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{MatchType, Type}

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
      case ScalaTokenTypes.tUPPER_BOUND if builder.isScala3 =>
        builder.advanceLexer()
        if (!Type.parse(builder)) builder.error(ScalaBundle.message("wrong.type"))
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer()

            if (!MatchType())
              builder.error(ScalaBundle.message("match.type.expected"))

            faultMarker.drop()
            true
          case _ =>
            faultMarker.rollbackTo()
            false
        }
      case _ =>
        faultMarker.rollbackTo()
        false
    }
  }
}
