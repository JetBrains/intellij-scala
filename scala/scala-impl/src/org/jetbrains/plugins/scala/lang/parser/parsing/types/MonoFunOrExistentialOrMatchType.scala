package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[MonoFunOrExistentialOrMatchType]] ::= [[InfixType]] ( ((‘=>’ | '?=>') [[Type]]) | [[ExistentialClause]] | [[MatchTypeSuffix]] )
 *                                       | [[DepFunParams]] '=>' [[Type]]
 */
object MonoFunOrExistentialOrMatchType {
  def parse(star: Boolean, isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (DepFunParams.parse()) {
      if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
        builder.advanceLexer()
        Type.parse(builder, star, isPattern)
      } else builder.error(ScalaBundle.message("fun.sign.expected"))
      marker.done(ScalaElementType.DEPENDENT_FUNCTION_TYPE)
      true
    } else if (InfixType.parse(builder, star, isPattern)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
          builder.advanceLexer() //Ate => or ?=>
          if (!Type.parse(builder, star, isPattern)) builder.error(ScalaBundle.message("wrong.type"))
          marker.done(ScalaElementType.TYPE)
        case ScalaTokenTypes.kFOR_SOME =>
          ExistentialClause.parse(builder)
          marker.done(ScalaElementType.EXISTENTIAL_TYPE)
        case ScalaTokenTypes.kMATCH =>
          builder.advanceLexer()
          MatchTypeSuffix.parse(builder)
          marker.done(ScalaElementType.MATCH_TYPE)
        case _ => marker.drop()
      }
      true
    } else {
      marker.rollbackTo()
      false
    }
  }
}
