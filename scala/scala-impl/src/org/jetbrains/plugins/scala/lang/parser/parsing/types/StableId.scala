package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.{InBracelessScala3, ParserUtils}

import scala.annotation.tailrec

/*
 * StableId ::= id
 *            | Path '.' id
 *            | Path '.' 'this'
 *            | [id '.'] 'super' [ClassQualifier] '.' id
 */

abstract class StableId(val forImport: Boolean = false) {
  def apply(element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer()
        if (builder.getTokenType == tDOT && !shouldStopBeforeDot()) {
          val nm = marker.precede()
          if (builder.lookAhead(tDOT, kTHIS) || builder.lookAhead(tDOT, kSUPER))
            marker.done(REFERENCE)
          else
            marker.done(element)
          builder.advanceLexer() // ate dot
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => parseQualId(nm, element)
            case ScalaTokenTypes.kTHIS => parseThisReference(nm, element)
            case ScalaTokenTypes.kSUPER => parseSuperReference(nm, element)
            case _ =>
              builder error ErrMsg("identifier.expected")
              nm.done(element)
              true
          }
        } else {
          marker.done(element)
          true
        }
      case ScalaTokenTypes.kTHIS => parseThisReference(marker, element)
      case ScalaTokenTypes.kSUPER => parseSuperReference(marker, element)
      case _ =>
        marker.drop()
        false

    }
  }

  def parseThisReference(marker: PsiBuilder.Marker, element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.advanceLexer()
    if (builder.getTokenType != tDOT) {
      builder.error(ErrMsg("dot.expected"))
      marker.done(THIS_REFERENCE)
      return true
    }
    marker.done(THIS_REFERENCE)
    parseEndIdentifier(marker.precede(), element)
  }

  def parseSuperReference(marker: PsiBuilder.Marker, element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.advanceLexer()
    if (builder.getTokenType != tDOT && builder.getTokenType != tLSQBRACKET) {
      builder.error(ErrMsg("dot.or.cq.expected"))
      marker.done(SUPER_REFERENCE)
      return true
    }
    parseClassQualifier()
    marker.done(SUPER_REFERENCE)
    parseEndIdentifier(marker.precede(), element)
  }

  def parseClassQualifier()(implicit builder: ScalaPsiBuilder): Unit = {
    if (builder.getTokenType != tLSQBRACKET) return
    builder.advanceLexer()
    builder.disableNewlines()
    if (builder.getTokenType != tIDENTIFIER) {
      builder.error(ErrMsg("identifier.expected"))
    }
    else {
      builder.advanceLexer()
    }

    if (builder.getTokenType != tRSQBRACKET) {
      builder.error(ErrMsg("rsqbracket.expected"))
    }
    else {
      builder.advanceLexer()
    }
    builder.restoreNewlinesState()
  }


  // For endings of 'this' and 'super' references
  def parseEndIdentifier(nm: PsiBuilder.Marker, element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      builder.error(ErrMsg("dot.expected"))
    }
    builder.advanceLexer()
    if (builder.getTokenType != ScalaTokenTypes.tIDENTIFIER) {
      builder.error(ErrMsg("identifier.expected"))
      nm.drop()
      return true
    }
    builder.advanceLexer()
    nm.done(element)
    if (builder.getTokenType == tDOT && !shouldStopBeforeDot()) {
      builder.advanceLexer()
      parseQualId(nm.precede(), element)
    } else {
      true
    }
  }

  // Begins from next id (not form dot)
  @tailrec
  final def parseQualId(marker: PsiBuilder.Marker, element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != tIDENTIFIER) {
      builder.error(ErrMsg("identifier.expected"))
      marker.done(element)
      return true
    }
    builder.advanceLexer() // ate identifier
    if (builder.getTokenType == tDOT && !builder.lookAhead(tDOT, kTYPE) && !shouldStopBeforeDot()) {
      marker.done(element)
      builder.advanceLexer() // ate dot
      parseQualId(marker.precede(), element)
    } else {
      marker.done(element)
      true
    }
  }

  private def shouldStopBeforeDot()(implicit builder: ScalaPsiBuilder): Boolean = {
    val s3f = builder.features
    val lookAhead = builder.lookAhead(1)
    lookAhead match {
      case `kTYPE` => true
      case `tUNDER` | `tLBRACE` | ScalaTokenType.GivenKeyword if forImport => true
      case `kMATCH` if builder.isScala3 => true
      case `tIDENTIFIER` | ScalaTokenType.WildcardStar if forImport && (s3f.`Scala 3 renaming imports` || s3f.`Scala 3 wildcard imports`) =>
        builder.predict(builder => (s3f.`Scala 3 wildcard imports` && builder.getTokenText == "*") ||
          s3f.`Scala 3 renaming imports` && {
            builder.advanceLexer()
            builder.getTokenText == "as"
          })
      case InBracelessScala3(`tIDENTIFIER`) => builder.isOutdentHere
      case _ => false
    }
  }
}

object StableId extends StableId(forImport = false)

object StableIdForImport extends StableId(forImport = true)
