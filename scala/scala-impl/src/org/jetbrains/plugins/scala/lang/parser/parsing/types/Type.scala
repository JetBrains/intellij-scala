package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type.parseWildcardStartToken

/**
 * Type ::= [[InfixTypePrefix]]
 *        | [[PolyFunOrTypeLambda]] (Scala 3)
 *        | _ SubtypeBounds
 *        | ? SubtypeBounds (Scala 3)
 * SubtypeBounds : == [>: Type] [<: Type]
 */
object Type extends Type {
  override protected def infixType: InfixType = InfixType

  // TODO: handle changes for later Dotty versions https://dotty.epfl.ch/docs/reference/changed-features/wildcards.html
  //   In Scala 3.0, both _ and ? are legal names for wildcards.
  //   In Scala 3.1, _ is deprecated in favor of ? as a name for a wildcard. A -rewrite option is available to rewrite one to the other.
  //   In Scala 3.2, the meaning of _ changes from wildcard to placeholder for type parameter.
  //   The Scala 3.1 behavior is already available today under the -strict setting.
  //   In Scala >2.13.6 or >2.12.14 and when -Xsource:3 is given then ? is also ok
  def parseWildcardStartToken()(implicit builder: ScalaPsiBuilder): Boolean =
    if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer()
      true
    } else builder.isScala3orSource3 && builder.tryParseSoftKeyword(ScalaTokenType.WildcardTypeQuestionMark)
}

trait Type {
  protected def infixType: InfixType

  def parse(
    builder:   ScalaPsiBuilder,
    star:      Boolean = false,
    isPattern: Boolean = false
  ): Boolean = {
    implicit val b: ScalaPsiBuilder = builder
    val typeMarker = builder.mark

    if (InfixTypePrefix.parse(star, isPattern)) {
      typeMarker.drop()
      true
    } else if (PolyFunOrTypeLambda.parse(star, isPattern)) {
      typeMarker.drop()
      true
    } else if (parseWildcardType(typeMarker, isPattern)) {
      true
    } else {
      builder.getTokenType match {
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
        if (!parse(builder, isPattern = isPattern)) {
          builder error ScalaBundle.message("wrong.type")
        }
        funMarker.done(ScalaElementType.TYPE)
      case _ =>
    }

    true
  }
}