package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.cc.CaptureSet

/*
 *  CompoundType ::= AnnotType {with AnnotType} [Refinement]
 *                 | Refinement
 */

object CompoundType extends Type {
  override def apply(star: Boolean, isPattern: Boolean, typeVariables: Boolean, inContextBound: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val compoundMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE if !builder.isScala3 =>
        // in scala 3 this is handled in simple type
        if (Refinement()) {
          compoundMarker.done(ScalaElementType.COMPOUND_TYPE)
          true
        } else {
          compoundMarker.drop()
          false
        }
      case _ =>
        if (!AnnotType(isPattern)) {
          compoundMarker.drop()
          false
        } else {
          var isCompound = false
          val parseAndLikeWith = builder.features.`& instead of with` && !builder.isScala3
          while (builder.getTokenType == ScalaTokenTypes.kWITH || (parseAndLikeWith && builder.getTokenText == "&")) {
            isCompound = true
            builder.advanceLexer() //Ate with or & (only in -Xsource:3)
            if (!AnnotType(isPattern)) {
              builder error ScalaBundle.message("wrong.type")
            }
          }
          val hasRefinement = Refinement()

          val captureTypeMarker = compoundMarker.precede()

          if (isCompound || hasRefinement) {
            compoundMarker.done(ScalaElementType.COMPOUND_TYPE)
          } else compoundMarker.drop()

          if (
            builder.features.`parses capture checking` &&
            {
              val ty = builder.getTokenType
              ty == ScalaTokenTypes.tIDENTIFIER || ty == ScalaTokenType.CaptureOperator
            } &&
            builder.getTokenText == "^" &&
            followingTokenMakesUpArrowCaptureOp
          ) {
            builder.remapCurrentToken(ScalaTokenType.CaptureOperator)
            builder.advanceLexer() // eat ^
            CaptureSet()
            captureTypeMarker.done(ScalaElementType.CAPTURE_TYPE)
          } else {
            captureTypeMarker.drop()
          }

          true
        }
    }
  }

  /**
   * From the compiler in compiler/src/dotty/tools/dotc/parsing/Parsers.scala:
   *
   * Disambiguation: a `^` is treated as a postfix operator meaning `^{cap}`
   *  if followed by `{`, `->`, or `?->`,
   *  or followed by a new line (significant or not),
   *  or followed by a token that cannot start an infix type.
   *  Otherwise it is treated as an infix operator.
   */
  private def followingTokenMakesUpArrowCaptureOp(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.predict { builder =>
      builder.getTokenText match {
        case "{" | "->" | "?->" => true
        case _ if builder.findPreviousNewLine.nonEmpty => true
        case _ if !canStartInfixTypeTokens.contains(builder.getTokenType) => true
        case _ => false
      }
    }
  }

  private val canStartInfixTypeTokens = TokenSet.orSet(
    // simpleLiteralTokens
    ScalaTokenTypes.LITERALS,
    ScalaTokenTypes.IDENTIFIER_TOKEN_SET,

    TokenSet.create(
      ScalaTokenTypes.kTHIS,
      ScalaTokenTypes.kSUPER,
      ScalaTokenTypes.tUNDER,
      ScalaTokenTypes.tLPARENTHESIS,
      ScalaTokenTypes.tLBRACE,
      ScalaTokenTypes.tAT
    )
  )
}