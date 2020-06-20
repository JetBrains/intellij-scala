package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import java.io.IOException

import com.intellij.lexer.{LexerBase, MergingLexerAdapter}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocAsteriskStripperLexer.contains
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType._

final class ScalaDocLexer() extends MergingLexerAdapter(
  new ScalaDocAsteriskStripperLexer(new _ScalaDocLexer),
  ScalaDocLexer.TokensToMerge
)

object ScalaDocLexer {
  private val TokensToMerge = TokenSet.create(DOC_COMMENT_DATA, DOC_WHITESPACE, DOC_INNER_CODE)
}

private final class ScalaDocAsteriskStripperLexer private[lexer](
  val myFlex: _ScalaDocLexer
) extends LexerBase {

  private var myBuffer         : CharSequence = _
  private var myBufferIndex    : Int          = 0
  private var myBufferEndOffset: Int          = 0
  private var myTokenEndOffset : Int          = 0
  private var myState          : Int          = 0
  private var myTokenType      : IElementType = _

  private var isAfterLineBreak: Boolean = false
  private var isInLeadingSpace: Boolean = false

  private var needItalic            : Boolean = false
  private var needCorrectAfterItalic: Boolean = false
  private var hasPreviousBold       : Boolean = false

  override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
    myBuffer = buffer
    myBufferIndex = startOffset
    myBufferEndOffset = endOffset
    myTokenType = null
    myTokenEndOffset = startOffset
    myFlex.reset(myBuffer, startOffset, endOffset, initialState)
  }

  override def getState: Int = myState

  override def getBufferSequence: CharSequence = myBuffer

  override def getBufferEnd: Int = myBufferEndOffset

  override def getTokenType: IElementType = {
    locateToken()
    myTokenType
  }
  override def getTokenStart: Int = {
    locateToken()
    myBufferIndex
  }
  override def getTokenEnd: Int = {
    locateToken()
    myTokenEndOffset
  }
  override def advance(): Unit = {
    locateToken()
    myTokenType = null
  }

  private def locateToken(): Unit = {
    if (myTokenType != null)
      return
    doLocateToken()

    if (myTokenType == DOC_WHITESPACE)
      isAfterLineBreak = CharArrayUtil.containLineBreaks(myBuffer, getTokenStart, getTokenEnd)
  }

  private def doLocateToken(): Unit = {
    if (myTokenEndOffset == myBufferEndOffset) {
      myTokenType = null
      myBufferIndex = myBufferEndOffset
      return
    }

    myBufferIndex = myTokenEndOffset

    if (isAfterLineBreak) {
      isAfterLineBreak = false
      isInLeadingSpace = true

      if (hasChar(myTokenEndOffset, '*') && !hasChar(myTokenEndOffset + 1, '/')) {
        myTokenEndOffset += 1
        myTokenType = DOC_COMMENT_LEADING_ASTERISKS
        return
      }
    }

    if (isInLeadingSpace) {
      isInLeadingSpace = false
      if (myFlex.yystate != _ScalaDocLexer.COMMENT_INNER_CODE) {
        myFlex.yybegin(_ScalaDocLexer.COMMENT_DATA_START)
      }
    }

    flexLocateToken()
  }

  @inline private def hasChar(idx: Int, c: Char): Boolean =
    !isEof(idx) && myBuffer.charAt(idx) == c

  @inline private def isEof(offset: Int = myTokenEndOffset): Boolean =
    offset >= myBufferEndOffset

  @inline private def skipWhiteSpaces(startIdx: Int): Int = {
    var idx = startIdx
    while (!isEof(idx) && myBuffer.charAt(idx).isWhitespace)
      idx += 1
    idx
  }

  @inline private def isInTagSpace(state: Int) = state match {
    case _ScalaDocLexer.PARAM_TAG_SPACE |
         _ScalaDocLexer.TAG_DOC_SPACE |
         _ScalaDocLexer.INLINE_TAG_NAME |
         _ScalaDocLexer.DOC_TAG_VALUE_IN_PAREN => true
    case _ => false
  }

  private def flexLocateToken(): Unit = try {
    if (needItalic) {
      needItalic = false
      myTokenType = DOC_ITALIC_TAG
      myBufferIndex -= 1
      myTokenEndOffset = myBufferIndex + 2
      needCorrectAfterItalic = true
      return
    }
    else if (needCorrectAfterItalic) {
      needCorrectAfterItalic = false
      myBufferIndex += 1
    }

    myState = myFlex.yystate
    myFlex.goTo(myBufferIndex)
    myTokenType = myFlex.advance
    myTokenEndOffset = myFlex.getTokenEnd

    if (
      myTokenType == DOC_BOLD_TAG &&
        myTokenEndOffset < myBufferEndOffset - 1 &&
        myBuffer.charAt(myTokenEndOffset) == '\'' &&
        myBuffer.charAt(myTokenEndOffset + 1) != '\''
    ) {
      needItalic = true
      myTokenType = DOC_ITALIC_TAG
      myTokenEndOffset -= 1
    }

    myTokenType match {
      case DOC_BOLD_TAG                      => hasPreviousBold = true
      case DOC_ITALIC_TAG if hasPreviousBold => hasPreviousBold = false
      case _                                 =>
    }
  } catch {
    case _: IOException => // Can't be
  }

  // maybe use com.intellij.util.text.CharArrayUtil.isEmptyOrSpaces?
  private def hasWhitespacesOnly(buffer: CharSequence, start: Int, end: Int): Boolean = {
    var i = start
    while (i < end) {
      if (buffer.charAt(i) > ' ') //see String#trim method
      return false
      i += 1
    }
    true
  }
}

object ScalaDocAsteriskStripperLexer {

  private def contains(char: Char, text: CharSequence, start: Int, end: Int): Boolean = {
    var idx = start
    while (start < end){
      if (text.charAt(idx) == char)
        return true
      idx += 1
    }
    false
  }
}