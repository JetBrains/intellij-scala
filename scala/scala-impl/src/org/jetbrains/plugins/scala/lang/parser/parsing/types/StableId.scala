package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

/*
 * StableId ::= id
 *            | Path '.' id
 *            | [id '.'] 'super' [ClassQualifier] '.' id
 */

object StableId {
  def apply(element: IElementType, forImport: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer()
        if (builder.getTokenType == tDOT && !shouldStopBeforeDot(forImport)(builder)) {
          val nm = marker.precede
          if (builder.lookAhead(tDOT, kTHIS) || builder.lookAhead(tDOT, kSUPER))
            marker.done(REFERENCE)
          else
            marker.done(element)
          builder.advanceLexer() // ate dot
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => parseQualId(nm, element, forImport)
            case ScalaTokenTypes.kTHIS => parseThisReference(nm, element, forImport)
            case ScalaTokenTypes.kSUPER => parseSuperReference(nm, element, forImport)
            case _ =>
              builder error ErrMsg("identifier.expected")
              nm.drop()
              true
          }
        } else {
          marker.done(element)
          true
        }
      case ScalaTokenTypes.kTHIS => parseThisReference(marker, element, forImport)
      case ScalaTokenTypes.kSUPER => parseSuperReference(marker, element, forImport)
      case _ =>
        marker.drop()
        false

    }
  }

  def parseThisReference(marker: PsiBuilder.Marker, element: IElementType, forImport: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val nm = marker.precede()
    builder.advanceLexer()
    if (builder.getTokenType != tDOT) {
      builder.error(ErrMsg("dot.expected"))
      marker.done(THIS_REFERENCE)
      nm.drop()
      return true
    }
    marker.done(THIS_REFERENCE)
    parseEndIdentifier(nm, element, forImport)
  }

  def parseSuperReference(marker: PsiBuilder.Marker, element: IElementType, forImport: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val nm = marker.precede()
    builder.advanceLexer()
    if (builder.getTokenType != tDOT && builder.getTokenType != tLSQBRACKET) {
      builder.error(ErrMsg("dot.or.cq.expected"))
      nm.drop()
      marker.done(SUPER_REFERENCE)
      return true
    }
    parseClassQualifier()
    marker.done(SUPER_REFERENCE)
    parseEndIdentifier(nm, element, forImport)
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
  def parseEndIdentifier(nm: PsiBuilder.Marker, element: IElementType, forImport: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
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
    if (builder.getTokenType == tDOT && !shouldStopBeforeDot(forImport)) {
      val nm1 = nm.precede()
      nm.done(element)
      builder.advanceLexer()
      parseQualId(nm1, element, forImport)
    } else {
      nm.done(element)
      true
    }
  }

  // Begins from next id (not form dot)
  @tailrec
  def parseQualId(marker: PsiBuilder.Marker, element: IElementType, forImport: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != tIDENTIFIER) {
      builder.error(ErrMsg("identifier.expected"))
      marker.drop()
      return true
    }
    builder.advanceLexer() // ate identifier
    if (builder.getTokenType == tDOT && !builder.lookAhead(tDOT, kTYPE) && !shouldStopBeforeDot(forImport)) {
      val nm = marker.precede
      marker.done(element)
      builder.advanceLexer() // ate dot
      parseQualId(nm, element, forImport)
    } else {
      marker.done(element)
      true
    }
  }

  private def shouldStopBeforeDot(forImport: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val s3f = builder.features
    val lookAhead = builder.lookAhead(1)
    lookAhead match {
      case `kTYPE` => true
      case `tUNDER` | `tLBRACE` | ScalaTokenType.GivenKeyword if forImport => true
      case `kMATCH` if builder.isScala3 => true
      case `tIDENTIFIER` if forImport && (s3f.`Scala 3 renaming imports` || s3f.`Scala 3 wildcard imports`) =>
        builder.predict(builder => (s3f.`Scala 3 wildcard imports` && builder.getTokenText == "*") ||
          s3f.`Scala 3 renaming imports` && {
            builder.advanceLexer()
            builder.getTokenText == "as"
          })
      case _ => false
    }
  }
}