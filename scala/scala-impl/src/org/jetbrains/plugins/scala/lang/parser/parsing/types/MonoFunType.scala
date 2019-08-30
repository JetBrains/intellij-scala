package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[MonoFunType]] ::= [[InfixType]] ‘=>’ [[Type]]
 */
object MonoFunType extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (InfixType.parse(builder)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE =>
          builder.advanceLexer() //Ate =>
          if (!parse()) {
            builder.error(ScalaBundle.message("wrong.type"))
          }
          marker.done(ScalaElementType.TYPE)
        case ScalaTokenTypes.kFOR_SOME =>
          ExistentialClause parse builder
          marker.done(ScalaElementType.EXISTENTIAL_TYPE)
        case _ => marker.drop()
      }
      true
    } else {
      marker.rollbackTo()
      false
    }
  }
}
