package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{ExistentialClause, InfixType, Type}

/*
 * TypePattern ::= Type (but it can't be InfixType => Type (because case A => B => C?))
 */
object TypePattern extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val typeMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        val parMarker = builder.mark()
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow | ScalaTokenTypes.tRPARENTHESIS =>
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
                builder.advanceLexer() //Ate => or ?=>
                if (!Type(isPattern = true)) {
                  builder error ScalaBundle.message("wrong.type")
                }
              case _ =>
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS =>
                builder.advanceLexer() //Ate )
              case _ =>
                builder error ScalaBundle.message("rparenthesis.expected")
            }
            builder.restoreNewlinesState()
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
                builder.advanceLexer() //Ate => or ?=>
              case _ =>
                builder error ScalaBundle.message("fun.sign.expected")
            }
            if (!Type(isPattern = true)) {
              builder error ScalaBundle.message("wrong.type")
            }
            typeMarker.done(ScalaElementType.TYPE_PATTERN)
            parMarker.drop()
            return true
          case _ =>
            builder.restoreNewlinesState()
            parMarker.rollbackTo()
        }
      case _ =>
    }
    if (!InfixType(isPattern = true)) {
      typeMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME =>
        ExistentialClause()
        typeMarker.done(ScalaElementType.TYPE_PATTERN)
        true
      case _ =>
        typeMarker.done(ScalaElementType.TYPE_PATTERN)
        true
    }
  }
}