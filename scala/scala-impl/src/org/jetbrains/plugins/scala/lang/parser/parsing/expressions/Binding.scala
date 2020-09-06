package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

/**
 * @author Aleksander Podkhalyuzin
 * @date 05.04.2009
 */

/**
 * Binding ::= (id | '_') [':' Type]
 */
object Binding extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        builder.mark.done(ScalaElementType.ANNOTATIONS)
        builder.advanceLexer()
      case _ =>
        paramMarker.drop()
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
        if (!ParamType.parse(builder)) builder error ErrMsg("wrong.type")
      case _ =>
    }

    paramMarker.done(ScalaElementType.PARAM)
    true
  }
}