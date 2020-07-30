package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.{InlineKeyword, OpaqueKeyword, OpenKeyword, TransparentKeyword}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

// See https://dotty.epfl.ch/docs/reference/soft-modifier.html
object SoftModifier extends ParsingRule{
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3) {
      return false
    }

    softModifiers.get(builder.getTokenText) match {
      case Some(tokenType) =>
        val marker = builder.mark()

        builder.remapCurrentToken(tokenType)
        builder.advanceLexer() // ate soft modifier

        // soft modifiers must me followed by a hard modifier, a definition start or another soft modifier
        if (checkFollowCondition()) {
          marker.drop()
          true
        } else {
          marker.rollbackTo()
          false
        }
      case None =>
        false
    }
  }

  private val softModifiers = Seq(
    OpaqueKeyword,
    InlineKeyword,
    TransparentKeyword,
    OpenKeyword,
  ).map(kw => kw.text -> kw).toMap

  private def checkFollowCondition()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (isDefinitionStartOrHardModifier(builder.getTokenType)) {
      return true
    }

    if (builder.getTokenType == ScalaTokenTypes.kCASE) {
      return isCaseDefinition(builder.lookAhead(0))
    }

    // check if there is another soft modifier
    if (softModifiers.contains(builder.getTokenText)) {
      val lookAheadMarker = builder.mark()

      try SoftModifier()
      finally lookAheadMarker.rollbackTo()
    } else {
      false
    }
  }

  private val isDefinitionStartOrHardModifier = Set(
    ScalaTokenTypes.kDEF,
    ScalaTokenTypes.kVAL,
    ScalaTokenTypes.kVAR,
    ScalaTokenTypes.kTYPE,

    ScalaTokenType.ClassKeyword,
    ScalaTokenType.TraitKeyword,
    ScalaTokenType.ObjectKeyword,
    ScalaTokenType.EnumKeyword,
    ScalaTokenType.GivenKeyword,

    ScalaTokenTypes.kPRIVATE,
    ScalaTokenTypes.kPROTECTED,
    ScalaTokenTypes.kFINAL,
    ScalaTokenTypes.kABSTRACT,
    ScalaTokenTypes.kOVERRIDE,
    ScalaTokenTypes.kIMPLICIT,
    ScalaTokenTypes.kSEALED,
    ScalaTokenTypes.kLAZY,
  )

  private val isCaseDefinition = Set[IElementType](
    ScalaTokenType.ClassKeyword,
    //ScalaTokenType.TraitKeyword,
    ScalaTokenType.ObjectKeyword,
  )
}
