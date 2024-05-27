package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/**
 * [[InfixTypePrefix]] ::= [[InfixType]] ( ((‘=>’ | '?=>') [[Type]])
 *                                         | [[ExistentialClause]]
 *                                         | [[MatchTypeSuffix]] )
 *                       | [[DepFunParams]] '=>' [[Type]]
 */
object InfixTypePrefix {
  def apply(star: Boolean, isPattern: Boolean, typeVariables: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    val rollbackMarker = builder.mark()
    if (builder.isScala3 && DepFunParams()) {
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
          builder.advanceLexer()
          Type(star, isPattern)
          rollbackMarker.drop()
          marker.done(ScalaElementType.DEPENDENT_FUNCTION_TYPE)
          return true
        case _ =>
          //builder.error(ScalaBundle.message("fun.sign.expected"))
      }
    }
    rollbackMarker.rollbackTo()

    val givenMarker = builder.mark()

    val isImplicitFunctionType =
      builder.isScala3 &&
        builder.lookAhead(ScalaTokenTypes.tLPARENTHESIS, ScalaTokenType.GivenKeyword)

    if (isImplicitFunctionType) {
      builder.advanceLexer() // '("
      builder.advanceLexer() // 'given'
    } else givenMarker.drop()

    if (InfixType(star, isPattern, typeVariables)) {
      if (isImplicitFunctionType) {
        if (builder.getTokenType == ScalaTokenTypes.tRPARENTHESIS)
          builder.advanceLexer()
        else builder.error(ScalaBundle.message("rparenthesis.expected"))
        givenMarker.done(ScalaElementType.TYPE_IN_PARENTHESIS)
      }

      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
          builder.advanceLexer() //Ate => or ?=>
          if (!Type(star, isPattern)) builder.error(ScalaBundle.message("wrong.type"))
          marker.done(ScalaElementType.TYPE)
        case ScalaTokenTypes.kFOR_SOME =>
          ExistentialClause()
          marker.done(ScalaElementType.EXISTENTIAL_TYPE)
        case InScala3(ScalaTokenTypes.kMATCH) =>
          builder.advanceLexer()
          MatchTypeSuffix()
          marker.done(ScalaElementType.MATCH_TYPE)
        case _ => marker.drop()
      }
      true
    } else {
      if (isImplicitFunctionType) givenMarker.drop()
      marker.rollbackTo()
      false
    }
  }
}
