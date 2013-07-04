package org.jetbrains.plugins.scala
package lang
package parser
package util

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.util.DebugPrint
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import parsing.builder.ScalaPsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import annotation.tailrec


object ParserUtils extends ParserUtilsBase {

  def lookAheadSeq(n: Int)(builder: PsiBuilder) = (1 to n).map(i => {
    val token = if (!builder.eof) builder.getTokenType else null
    builder.advanceLexer()
    token
  })

  //Write element node
  def eatElement(builder: PsiBuilder, elem: IElementType) {
    if (!builder.eof()) {
      builder.advanceLexer() // Ate something
    }
    ()

  }

  def parseTillLast(builder: PsiBuilder, lastSet: TokenSet) {
    while (!builder.eof() && !lastSet.contains(builder.getTokenType)) {
      builder.advanceLexer()
      DebugPrint println "an error"
    }

    if (builder.eof()) /*builder error "unexpected end of file"; */ return

    if (lastSet.contains(builder.getTokenType)) builder.advanceLexer()
    return
  }

  def eatSeqWildcardNext(builder: PsiBuilder): Boolean = {
    val marker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer()
      if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
              builder.getTokenText == "*") {
        builder.advanceLexer()
        marker.done(ScalaElementTypes.SEQ_WILDCARD)
        true
      } else {
        marker.rollbackTo()
        false
      }
    } else {
      marker.drop()
      false
    }
  }


  def build(t : IElementType, builder : PsiBuilder)  (inner : => Boolean) : Boolean = {
    val marker = builder.mark
    val parsed = inner
    if (parsed) marker.done(t) else marker.rollbackTo()
    parsed
  }

  def isAssignmentOperator(id: String) = id.charAt(id.length - 1) match {
    case '=' if id != "<=" && id != ">=" && id != "!=" && (id.charAt(0) != '=' || id == "=") => true
    case _ => false
  }

  //Defines priority
  def priority(id: String, assignments: Boolean = false): Int = {
    if (assignments && isAssignmentOperator(id)) {
      return 10
    }
    id.charAt(0) match {
      case '~' | '#' | '@' | '?' | '\\' => 0 //todo: other special characters?
      case '*' | '/' | '%' => 1
      case '+' | '-' => 2
      case ':' => 3
      case '=' | '!' => 4
      case '<' | '>' => 5
      case '&' => 6
      case '^' => 7
      case '|' => 8
      case _ => 9
    }
  }

  def caseLookAheadFunction(builder: ScalaPsiBuilder): IElementType = {
    val marker: Marker = builder.mark
    builder.advanceLexer()
    val res = builder.getTokenType
    marker.rollbackTo()
    res
  }

  @tailrec
  def parseLoopUntilRBrace(builder: ScalaPsiBuilder, fun: () => Unit, braceReported: Boolean = false) {
    var br = braceReported
    fun()
    builder.getTokenType match {
      case ScalaTokenTypes.tRBRACE =>
        builder.advanceLexer()
        return
      case ScalaTokenTypes.tLBRACE => //to avoid missing '{'
        if (!braceReported) {
          builder error ErrMsg("rbrace.expected")
          br = true
        }
        var balance = 1
        builder.advanceLexer()
        while (balance != 0 && !builder.eof) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => balance -= 1
            case ScalaTokenTypes.tLBRACE => balance += 1
            case _ =>
          }
          builder.advanceLexer()
        }
        if (builder.eof)
          return
      case _ =>
        if (!braceReported) {
          builder error ErrMsg("rbrace.expected")
          br = true
        }
        builder.advanceLexer()
        if (builder.eof) {
          return
        }
    }
    parseLoopUntilRBrace(builder, fun, br)
  }

  def elementCanStartStatement(element: IElementType, builder: ScalaPsiBuilder): Boolean = {
    element match {
      case ScalaTokenTypes.kCATCH => false
      case ScalaTokenTypes.kELSE => false
      case ScalaTokenTypes.kEXTENDS => false
      case ScalaTokenTypes.kFINALLY => false
      case ScalaTokenTypes.kMATCH => false
      case ScalaTokenTypes.kWITH => false
      case ScalaTokenTypes.kYIELD => false
      case ScalaTokenTypes.tCOMMA => false
      case ScalaTokenTypes.tDOT => false
      case ScalaTokenTypes.tSEMICOLON => false
      case ScalaTokenTypes.tCOLON => false
      case ScalaTokenTypes.tASSIGN => false
      case ScalaTokenTypes.tFUNTYPE => false
      case ScalaTokenTypes.tCHOOSE => false
      case ScalaTokenTypes.tUPPER_BOUND => false
      case ScalaTokenTypes.tLOWER_BOUND => false
      case ScalaTokenTypes.tVIEW => false
      case ScalaTokenTypes.tINNER_CLASS => false
      case ScalaTokenTypes.tLSQBRACKET => false
      case ScalaTokenTypes.tRSQBRACKET => false
      case ScalaTokenTypes.tRPARENTHESIS => false
      case ScalaTokenTypes.tRBRACE => false
      case ScalaTokenTypes.kCASE =>
        caseLookAheadFunction(builder) match {
          case ScalaTokenTypes.kOBJECT => true
          case ScalaTokenTypes.kCLASS => true
          case _ => false
        }
      case _ => true
    }
  }
}
