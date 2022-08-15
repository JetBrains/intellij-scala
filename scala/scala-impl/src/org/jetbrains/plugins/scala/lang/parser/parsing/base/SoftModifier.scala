package org.jetbrains.plugins.scala.lang.parser.parsing.base

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaModifierTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

// See https://dotty.epfl.ch/docs/reference/soft-modifier.html
object SoftModifier extends ParsingRule {

  private val modifiers = Seq(
    Inline,
    Transparent,
    Open,
    Infix,
    Opaque,
  )

  import ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = SoftModifier(builder) match {
    case Some(remappedTokenType) if isAllowed(remappedTokenType) =>
      val marker = builder.mark()

      builder.remapCurrentToken(remappedTokenType)
      builder.advanceLexer() // ate soft modifier

      // soft modifiers must me followed either by:
      // * a hard modifier;
      // * a definition start;
      // * another soft modifier;
      val nextTokenType = builder.getTokenType
      val result = nextTokenType match {
        case `kCASE` =>
          isCaseDefinition.contains(builder.lookAhead(1))
        case _ =>
          if (isDefinitionStart.contains(nextTokenType) || isHardModifier.contains(nextTokenType))
            true
          else {
            val maybeSoftModifier = SoftModifier(builder)
            maybeSoftModifier match {
              case Some(_) =>
                isFollowedBySoftModifier()
              case None =>
                false
            }
          }
      }

      if (result) marker.drop()
      else {
        marker.rollbackTo()
        builder.remapCurrentToken(ScalaTokenTypes.tIDENTIFIER)
      }

      result
    case _ => false
  }

  private def isAllowed(tokenType: ScalaModifierTokenType)(implicit builder: ScalaPsiBuilder): Boolean =
    builder.isScala3 || isAllowedInSource3(tokenType)

  private def isAllowedInSource3(tokenType: ScalaModifierTokenType)(implicit builder: ScalaPsiBuilder): Boolean = {
    val modifier = tokenType.modifier
    if (modifier == Open || modifier == Infix)
      builder.features.`soft keywords open and infix`
    else
      false
  }

  private def SoftModifier(builder: ScalaPsiBuilder): Option[ScalaModifierTokenType] =
    Option(ScalaModifier.byText(builder.getTokenText))
      .filter(modifiers.contains)
      .map(ScalaModifierTokenType(_))

  private def isFollowedBySoftModifier()(implicit builder: ScalaPsiBuilder): Boolean = {
    // check if there is another soft modifier
    val lookAheadMarker = builder.mark()
    try this ()
    finally lookAheadMarker.rollbackTo()
  }

  private val isDefinitionStart = TokenSet.create(
    kDEF,
    kVAL,
    kVAR,
    kTYPE,

    ClassKeyword,
    TraitKeyword,
    ObjectKeyword,
    EnumKeyword,
    GivenKeyword,
  )

  private val isHardModifier = TokenSet.create(
    kPRIVATE,
    kPROTECTED,
    kFINAL,
    kABSTRACT,
    kOVERRIDE,
    kIMPLICIT,
    kSEALED,
    kLAZY,
  )

  private val isCaseDefinition = TokenSet.create(
    ClassKeyword,
    //TraitKeyword,
    ObjectKeyword,
  )
}