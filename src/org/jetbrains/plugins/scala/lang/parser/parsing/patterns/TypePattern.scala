package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{ExistentialClause, InfixType, Type}

/**
 * @author Alexander Podkhalyuzin
 *         Date: 29.02.2008
 */

/*
 * TypePattern ::= Type (but it can't be InfixType => Type (because case A => B => C?))
 */
object TypePattern extends TypePattern {
  override protected val `type` = Type
  override protected val infixType = InfixType
}

trait TypePattern {
  protected val `type`: Type
  protected val infixType: InfixType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val typeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        val parMarker = builder.mark
        builder.advanceLexer() //Ate (
        builder.disableNewlines
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE | ScalaTokenTypes.tRPARENTHESIS =>
            if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
              builder.advanceLexer() //Ate =>
              if (!`type`.parse(builder, star = false, isPattern = true)) {
                builder error ScalaBundle.message("wrong.type")
              }
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS =>
                builder.advanceLexer() //Ate )
              case _ =>
                builder error ScalaBundle.message("rparenthesis.expected")
            }
            builder.restoreNewlinesState
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE =>
                builder.advanceLexer() //Ate =>
              case _ =>
                builder error ScalaBundle.message("fun.sign.expected")
            }
            if (!`type`.parse(builder, star = false, isPattern = true)) {
              builder error ScalaBundle.message("wrong.type")
            }
            typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
            parMarker.drop()
            return true
          case _ =>
            builder.restoreNewlinesState
            parMarker.rollbackTo()
        }
      case _ =>
    }
    if (!infixType.parse(builder, star = false, isPattern = true)) {
      typeMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME =>
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
        true
      case _ =>
        typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
        true
    }
  }
}