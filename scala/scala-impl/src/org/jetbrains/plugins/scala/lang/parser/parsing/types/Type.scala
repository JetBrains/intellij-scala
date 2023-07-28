package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type.parseWildcardStartToken

/**
 * {{{
 * Type ::= [[InfixTypePrefix]]
 *        | [[PolyFunOrTypeLambda]] (Scala 3)
 *        | _ SubtypeBounds
 *        | ? SubtypeBounds (Scala 3)
 * SubtypeBounds : == [>: Type] [<: Type]
 * }}}
 */
object Type extends Type {
  override protected def infixType: InfixType = InfixType

  // TODO: handle changes for later Dotty versions https://dotty.epfl.ch/docs/reference/changed-features/wildcards.html
  //   In Scala 3.0, both _ and ? are legal names for wildcards.
  //   In Scala 3.1, _ is deprecated in favor of ? as a name for a wildcard. A -rewrite option is available to rewrite one to the other.
  //   In Scala 3.2, the meaning of _ changes from wildcard to placeholder for type parameter.
  //   The Scala 3.1 behavior is already available today under the -strict setting.
  //   In Scala >2.13.6 or >2.12.14 and when -Xsource:3 is given then ? is also ok
  def parseWildcardStartToken()(implicit builder: ScalaPsiBuilder): Boolean = {
    val underscoresDisabled  = builder.underscoreWildcardsDisabled
    val qMarkWildcardEnabled = builder.features.`? as wildcard marker`

    if (!underscoresDisabled && builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer()
      true
    } else qMarkWildcardEnabled && builder.tryParseSoftKeyword(ScalaTokenType.WildcardTypeQuestionMark)
  }
}

trait Type {
  protected def infixType: InfixType

  def apply(star: Boolean = false, isPattern: Boolean = false, typeVariables: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val typeMarker = builder.mark()

    if (InfixTypePrefix(star, isPattern, typeVariables)) {
      typeMarker.drop()
      true
    } else if (PolyFunOrTypeLambda(star, isPattern)) {
      typeMarker.drop()
      true
    } else if (parseWildcardType(typeMarker, isPattern)) {
      true
    } else {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER =>
          val refMarker = builder.mark()
          builder.remapCurrentToken(ScalaTokenTypes.tIDENTIFIER)
          builder.advanceLexer()
          refMarker.done(ScalaElementType.REFERENCE)
          typeMarker.done(ScalaElementType.SIMPLE_TYPE)
          true
        case ScalaTokenTypes.tIDENTIFIER if builder.getTokenText == "*" =>
          typeMarker.drop()
          true
        case _ =>
          typeMarker.drop()
          false
      }
    }
  }

  private def parseWildcardType(typeMarker: PsiBuilder.Marker, isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!parseWildcardStartToken())
      return false

    Bounds.parseSubtypeBounds()
    typeMarker.done(ScalaElementType.WILDCARD_TYPE)

    // TODO: looks like this is a dead code, no tests trigger breakpoint inside, leaving it just in case...
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        val funMarker = typeMarker.precede()
        builder.advanceLexer() //Ate =>
        if (!Type(isPattern = isPattern)) {
          builder.error(ScalaBundle.message("wrong.type"))
        }
        funMarker.done(ScalaElementType.TYPE)
      case _ =>
    }

    true
  }
}