package org.jetbrains.plugins.scala
package lang
package parser
package util

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.DebugPrint

import scala.annotation.tailrec
import scala.collection.immutable.IndexedSeq
import scala.meta.intellij.IdeaUtil


object ParserUtils extends ParserUtilsBase {
  def lookAheadSeq(n: Int)(builder: PsiBuilder): IndexedSeq[IElementType] = (1 to n).map(_ => {
    val token = if (!builder.eof) builder.getTokenType else null
    builder.advanceLexer()
    token
  })

  def lookBack(psiBuilder: PsiBuilder, n: Int): IElementType = {
    @scala.annotation.tailrec
    def lookBackImpl(step: Int, all: Int): IElementType = {
      psiBuilder.rawLookup(step) match {
        case ws if TokenSets.WHITESPACE_OR_COMMENT_SET.contains(ws) => lookBackImpl(step-1, all)
        case other if all == 0 => other
        case other => lookBackImpl(step-1, all-1)
      }
    }

    lookBackImpl(-1, n)
  }

  def lookBack(psiBuilder: PsiBuilder): IElementType = lookBack(psiBuilder, 1)
  
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


  /** Defines the precedence of an infix operator, according
    * to its first character.
    *
    * @param id The identifier
    * @param assignments Consider assignment operators have lower priority than other non-special characters
    * @return An integer value. Lower value means higher precedence
    */
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
  
  def parseBalancedParenthesis(builder: ScalaPsiBuilder, accepted: TokenSet, count: Int = 1): Boolean = {
    var seen = 0
    
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        var count = 1
        builder.advanceLexer()
        
        while (count > 0 && !builder.eof()) {
          builder.getTokenType match {
            case ScalaTokenTypes.tLPARENTHESIS => count += 1
            case ScalaTokenTypes.tRPARENTHESIS => count -= 1
            case acc if accepted.contains(acc) => seen += 1
            case o => return false
          }
          
          builder.advanceLexer()
        }
      case _ => 
    }
    
    seen == count
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
  
  private def isTestFile(builder: ScalaPsiBuilder): Boolean = {
    ApplicationManager.getApplication.isUnitTestMode &&
      getPsiFile(builder).exists(file => file.getVirtualFile.isInstanceOf[LightVirtualFileBase])
  }
  
  def hasTextBefore(builder: ScalaPsiBuilder, text: String): Boolean = {
    Option(builder.getLatestDoneMarker).exists {
      marker =>
        StringUtil.equals(builder.getOriginalText.subSequence(marker.getStartOffset, marker.getEndOffset - 1), text)
    }
  }
  
  def isBackticked(name: String): Boolean = name != "`" && name.startsWith("`") && name.endsWith("`")
  
  def isCurrentVarId(builder: PsiBuilder): Boolean = {
    val txt = builder.getTokenText
    !txt.isEmpty && Character.isUpperCase(txt.charAt(0)) || isBackticked(txt)
  }
  
  def parseVarIdWithWildcardBinding(builder: PsiBuilder, rollbackMarker: PsiBuilder.Marker): Boolean = {
    if (!ParserUtils.isCurrentVarId(builder)) builder.advanceLexer() else {
      rollbackMarker.rollbackTo()
      return false
    }
    
    builder.advanceLexer() // @
    if (ParserUtils.eatSeqWildcardNext(builder)) {
      rollbackMarker.done(ScalaElementTypes.NAMING_PATTERN)
      true
    } else {
      rollbackMarker.rollbackTo()
      false
    }
  }
  
  def isIdBindingEnabled(builder: ScalaPsiBuilder): Boolean = isTestFile(builder) || builder.isIdBindingEnabled
  
  def isTrailingCommasEnabled(builder: ScalaPsiBuilder): Boolean = 
    ScalaProjectSettings.getInstance(builder.getProject).getTrailingCommasMode match {
      case ScalaProjectSettings.TrailingCommasMode.Enabled => true 
      case ScalaProjectSettings.TrailingCommasMode.Auto => isTestFile(builder) || builder.isTrailingCommasEnabled
      case ScalaProjectSettings.TrailingCommasMode.Disabled => false
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
  
  def hasMeta(builder: PsiBuilder): Boolean = !ScStubElementType.isStubBuilding &&
    !DumbService.isDumb(builder.getProject) && getPsiFile(builder).exists {
    file => IdeaUtil.inModuleWithParadisePlugin(file)
  }
  
  def getPsiFile(builder: PsiBuilder): Option[PsiFile] = {
    val delegate = builder match {
      case adapterBuilder: PsiBuilderAdapter => adapterBuilder.getDelegate
      case _ => builder
    }
    
    Option(delegate.getUserDataUnprotected(FileContextUtil.CONTAINING_FILE_KEY))
  }
}
