package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[MonoFunType]] ::= [ 'given' ] [[InfixType]] ‘=>’ [[Type]]
 *                   | [[InfixType]] [ [[ExistentialClause]] | [[MatchTypeSuffix]] ]
 *                   | [[DepFunParams]] '=>' [[Type]]
 */
object MonoFunType extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    val isImplicitFunctionType =
      builder.getTokenText match {
        case ScalaTokenType.Given.debugName =>
          builder.remapCurrentToken(ScalaTokenType.Given)
          builder.advanceLexer()
          true
        case _ => false
      }

    if (DepFunParams.parse() && !isImplicitFunctionType) {
      if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
        builder.advanceLexer()
        if (!Type.parse()) builder.error(ScalaBundle.message("wrong.type"))
      } else builder.error(ScalaBundle.message("fun.sign.expected"))

      marker.done(ScalaElementType.DEPENDENT_FUNCTION_TYPE)
      true
    } else if (InfixType.parse(builder)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE =>
          builder.advanceLexer() //Ate =>
          if (!Type.parse()) builder.error(ScalaBundle.message("wrong.type"))
          marker.done(ScalaElementType.TYPE)
        case _ if isImplicitFunctionType =>
          marker.rollbackTo()
          return false
        case ScalaTokenTypes.kFOR_SOME =>
          ExistentialClause.parse(builder)
          marker.done(ScalaElementType.EXISTENTIAL_TYPE)
        case ScalaTokenTypes.kMATCH =>
          MatchTypeSuffix.parse()
          marker.done(ScalaElementType.MATCH_TYPE)
        case _ => marker.drop()
      }
      true
    } else if (isImplicitFunctionType) {
      marker.rollbackTo()
      false
    } else {
      marker.rollbackTo()
      false
    }
  }
}
