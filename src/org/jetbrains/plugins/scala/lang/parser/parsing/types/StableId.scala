package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import ScalaElementTypes._
import builder.ScalaPsiBuilder
import com.intellij.psi.tree.IElementType

/** 
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 * StableId ::= id
 *            | Path '.' id
 *            | [id '.'] 'super' [ClassQualifier] '.' id
 */

object StableId extends ParserNode {

  def parse(builder: ScalaPsiBuilder, element: IElementType): Boolean = parse(builder, false, element)

  def parse(builder: ScalaPsiBuilder, forImport: Boolean, element: IElementType): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer()
        if (stopAtImportEnd(builder, forImport)) {
          marker.done(element)
          return true
        } else if (builder.getTokenType == tDOT && !lookAhead(builder, tDOT, kTYPE)) {
          val nm = marker.precede
          if (lookAhead(builder, tDOT, kTHIS) || lookAhead (builder, tDOT, kSUPER))
            marker.done(REFERENCE)
          else 
            marker.done(element)
          builder.advanceLexer()
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => return parseQualId(builder, nm, element, forImport)
            case ScalaTokenTypes.kTHIS => return parseThisReference(builder, nm, element, forImport)
            case ScalaTokenTypes.kSUPER => return parseSuperReference(builder, nm, element, forImport)
            case _ =>
              builder error ErrMsg("identifier.expected")
              nm.drop()
              return true
          }
        }
        marker.done(element)
        true
      }
      case ScalaTokenTypes.kTHIS => parseThisReference(builder, marker, element, forImport)
      case ScalaTokenTypes.kSUPER => parseSuperReference(builder, marker, element, forImport)
      case _ => {
        marker.drop()
        false
      }

    }
  }

  def parseThisReference(builder: ScalaPsiBuilder, marker: PsiBuilder.Marker, element: IElementType, forImport: Boolean): Boolean = {
    val nm = marker.precede()
    builder.advanceLexer()
    if (builder.getTokenType != tDOT) {
      builder.error(ErrMsg("dot.expected"))
      marker.done(THIS_REFERENCE)
      nm.drop()
      return true
    }
    marker.done(THIS_REFERENCE)
    parseEndIdentifier(builder, nm, element, forImport)
  }

  def parseSuperReference(builder: ScalaPsiBuilder, marker: PsiBuilder.Marker, element: IElementType, forImport: Boolean): Boolean = {
    val nm = marker.precede()
    builder.advanceLexer()
    if (builder.getTokenType != tDOT && builder.getTokenType != tLSQBRACKET) {
      builder.error(ErrMsg("dot.or.cq.expected"))
      nm.drop()
      marker.done(SUPER_REFERENCE)
      return true
    }
    parseClassQualifier(builder)
    marker.done(SUPER_REFERENCE)
    parseEndIdentifier(builder, nm, element, forImport)
  }

  def parseClassQualifier(builder: ScalaPsiBuilder) {
    if (builder.getTokenType != tLSQBRACKET) return
    builder.advanceLexer()
    builder.disableNewlines
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
    builder.restoreNewlinesState
  }


  // For endings of 'this' and 'super' references
  def parseEndIdentifier(builder: ScalaPsiBuilder, nm: PsiBuilder.Marker, element: IElementType, forImport: Boolean): Boolean = {
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
    if (stopAtImportEnd(builder, forImport)) {
      nm.done(element)
      true
    } else if (builder.getTokenType == tDOT && !lookAhead(builder, tDOT, kTYPE)) {
      val nm1 = nm.precede()
      nm.done(element)
      builder.advanceLexer()
      parseQualId(builder, nm1, element, forImport)
    } else {
      nm.done(element)
      true
    }
  }

  // Begins from next id (not form dot)
  def parseQualId(builder: ScalaPsiBuilder, marker: PsiBuilder.Marker, element: IElementType, forImport: Boolean): Boolean = {
    if (builder.getTokenType != tIDENTIFIER) {
      builder.error(ErrMsg("identifier.expected"))
      marker.drop()
      return true
    }
    builder.advanceLexer() // ate identifier
    if (stopAtImportEnd(builder, forImport)) {
      marker.done(element)
      true
    } else if (builder.getTokenType == tDOT && !lookAhead(builder, tDOT, kTYPE)) {
      val nm = marker.precede
      marker.done(element)
      builder.advanceLexer() // ate dot
      parseQualId(builder, nm, element, forImport)
    } else {
      marker.done(element)
      true
    }
  }

  def stopAtImportEnd(builder: ScalaPsiBuilder, forImport: Boolean) = forImport && isImportEnd(builder)

  def isImportEnd(builder: ScalaPsiBuilder): Boolean = {
    lookAhead(builder, tDOT, tUNDER) || lookAhead(builder, tDOT, tLBRACE)
  }

}