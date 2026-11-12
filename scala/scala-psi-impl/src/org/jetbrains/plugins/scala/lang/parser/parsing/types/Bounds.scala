package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.GivenKeyword
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.cc.CaptureSet

object Bounds {
  val UPPER      : String = "<:"
  val LOWER      : String = ">:"
  val VIEW       : String = "<%" // deprecated in 2.13

  def apply(bound: String)(implicit builder: ScalaPsiBuilder): Boolean =
    if (builder.getTokenText == bound) {
      builder.advanceLexer()
      // Only lower/upper bounds admit capture sets, e.g. C^ <: {io} or C^ >: {}.
      // Legacy Scala 2 view bounds always require a type: in T <% {}, the braces
      // form an empty refinement type, so keep that case on the ordinary type parser.
      if (bound != VIEW && isCaptureSetBound) {
        CaptureSet.parseBound()
      } else if (!Type()) {
        builder.error(ErrMsg("wrong.type"))
      }
      true
    } else false

  // Scala 3.8.0 through 3.10.1 allow capture sets as lower/upper bounds when
  // capture checking is enabled. A declaration after '{' starts a refinement.
  private def isCaptureSetBound(implicit builder: ScalaPsiBuilder): Boolean =
    builder.features.`parses capture checking` && builder.getTokenType == tLBRACE && {
      val firstTokenInBraces = builder.lookAhead(1)
      !startsRefinementDeclaration(firstTokenInBraces)
    }

  private def startsRefinementDeclaration(tokenType: IElementType): Boolean = tokenType match {
    case `kDEF` | `kVAL` | `kVAR` | `kTYPE` | GivenKeyword => true
    case _ => false
  }

  def parseSubtypeBounds()(implicit builder: ScalaPsiBuilder): Unit = {
    Bounds(Bounds.LOWER)
    Bounds(Bounds.UPPER)
  }
}
