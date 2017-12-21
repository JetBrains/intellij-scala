package org.jetbrains.plugins.scala
package lang
package parser
package util

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}
import org.jetbrains.plugins.scala.util.DebugPrint

import scala.annotation.tailrec
import scala.collection.immutable.IndexedSeq


object ParserUtils extends ParserUtilsBase {
  def lookAheadSeq(n: Int)(builder: PsiBuilder): IndexedSeq[IElementType] = (1 to n).map(_ => {
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


  def build(t: IElementType, builder: PsiBuilder)(inner: => Boolean): Boolean = {
    val marker = builder.mark
    val parsed = inner
    if (parsed) marker.done(t) else marker.rollbackTo()
    parsed
  }

  def isAssignmentOperator: String => Boolean = {
    case "==" | "!=" | "<=" | ">=" => false
    case "=" => true
    case id => id.head != '=' && id.last == '='
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

  def countNewLinesBeforeCurrentTokenRaw(builder: ScalaPsiBuilder): Int = {
    var i = 1
    while (i < builder.getCurrentOffset && TokenSets.WHITESPACE_OR_COMMENT_SET.contains(builder.rawLookup(-i))) i += 1
    val textBefore = builder.getOriginalText.subSequence(builder.rawTokenTypeStart(-i + 1), builder.rawTokenTypeStart(0)).toString
    if (!textBefore.contains('\n')) return 0
    val lines = s"start $textBefore end".split('\n')
    if (lines.exists(_.forall(StringUtil.isWhiteSpace))) 2
    else 1
  }
  
  def isTrailingCommasEnabled(builder: ScalaPsiBuilder): Boolean = {
    ApplicationManager.getApplication.isUnitTestMode && 
      getPsiFile(builder).exists(file => file.getVirtualFile.isInstanceOf[LightVirtualFileBase]) ||
      builder.asInstanceOf[ScalaPsiBuilderImpl].isTrailingCommasEnabled
  }

  def isTrailingComma(builder: ScalaPsiBuilder, expectedBrace: IElementType): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tCOMMA) return false

    isTrailingCommasEnabled(builder) && {
      val marker = builder.mark()

      builder.advanceLexer()
      val s = builder.getTokenType == expectedBrace && countNewLinesBeforeCurrentTokenRaw(builder) > 0

      marker.rollbackTo()

      s
    }
  }

  def eatTrailingComma(builder: ScalaPsiBuilder, expectedBrace: IElementType): Boolean = {
    if (!isTrailingComma(builder, expectedBrace)) return false

    builder.advanceLexer() //eat `,`

    true
  }
  
  def getPsiFile(builder: PsiBuilder): Option[PsiFile] = {
    val delegate = builder match {
      case adapterBuilder: PsiBuilderAdapter => adapterBuilder.getDelegate
      case _ => builder
    }
    
    Option(delegate.getUserDataUnprotected(FileContextUtil.CONTAINING_FILE_KEY))
  }
}
