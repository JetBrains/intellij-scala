package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.ScalaDocMarkdownFlavour

class _ScalaDocMarkdownLexer extends LexerBase {
  private var originalBuffer: CharSequence = _
  private var originalStartOffset: Int = _
  private var originalEndOffset: Int = _

  private val delegate = (new ScalaDocMarkdownFlavour).createInlinesLexer()
  private var delegateState = 0

  private var myState = 0
  private var offset = 0
  private var tokenEnd = 0

  private val STATE_WIDTH = 4

  override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
    originalBuffer = buffer
    originalStartOffset = startOffset
    originalEndOffset = endOffset

    delegate.start(buffer, startOffset, endOffset, initialState >> STATE_WIDTH)
    myState = initialState & ((1 << STATE_WIDTH) - 1)

    offset = originalStartOffset
    calcTokenEnd()
  }

  override def getState: Int =
    (delegate.getState << STATE_WIDTH) | myState

  private def platformType(@Nullable tokenType: org.intellij.markdown.IElementType): IElementType = {
    if (tokenType == null) return null
    _ScalaDocMarkdownLexer.DELEGATE_MAP.getOrElse(tokenType, ScalaDocTokenType.DOC_COMMENT_DATA)
  }

  override def getTokenType: IElementType = myState match {
    case _ScalaDocMarkdownLexer.DELEGATE =>
      platformType(delegate.getType)
    case _ScalaDocMarkdownLexer.LEADING_ASTERISK =>
      ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
    case _ScalaDocMarkdownLexer.START =>
      ScalaDocTokenType.DOC_COMMENT_START
    case _ScalaDocMarkdownLexer.END =>
      if (offset < originalEndOffset) ScalaDocTokenType.DOC_COMMENT_END else null // Over when we advance from the end.
    case x if _ScalaDocMarkdownLexer.isWhitespaceState(x) =>
      ScalaDocTokenType.DOC_WHITESPACE
  }

  override def getTokenStart: Int = offset

  override def getTokenEnd: Int = tokenEnd

  private def unpauseDelegate(): Unit = {
    myState = _ScalaDocMarkdownLexer.DELEGATE
    delegate.start(originalBuffer, offset, originalEndOffset, delegateState)
    // Note that we skipped past the previous token without adjusting the state.
    // Luckily for us, the state after a newline (which is the only time we switch to the paused mode)
    // is completely reset. So... this is mostly fine.
    // If there are bugs, it'd probably be best to clone the whole .flex and modify that instead.

    if (delegate.getType == MarkdownTokenTypes.EOL) {
      myState = _ScalaDocMarkdownLexer.LINE_SPACE
    }
  }

  /**
   * Does the special lexing after a new Markdown line.
   *
   * @return whether lexing has been overridden
   */
  private def startNewMarkdownLine(): Boolean = {
    // First, find the first non-whitespace, non-newline character after `offset`:
    val firstNonWhitespaceIndex = (offset until originalEndOffset)
      .find { idx =>
        val c = originalBuffer.charAt(idx)
        c == '\n' || !c.isWhitespace
      }

    firstNonWhitespaceIndex.exists { index =>
      originalBuffer.charAt(index) match {
        case c if c != '\n' && originalBuffer.charAt(offset).isWhitespace =>
          // The line started with whitespaces followed by something else than a newline.
          // Make sure that these whitespaces become their own token.
          myState = _ScalaDocMarkdownLexer.WS_AFTER_LEADING_ASTERISK
          true
        case _ =>
          false
      }
    }
  }

  private def calcTokenEnd(): Unit = {
    tokenEnd = myState match {
      case _ScalaDocMarkdownLexer.DELEGATE => delegate.getTokenEnd
      case _ScalaDocMarkdownLexer.LEADING_ASTERISK =>
        offset + 1
      case _ScalaDocMarkdownLexer.START =>
        offset + 3 // /** is always 3 characters
      case _ScalaDocMarkdownLexer.END =>
        offset + 2 // "*/" is always 2 characters
      case x if _ScalaDocMarkdownLexer.isWhitespaceState(x) =>
        (offset until originalEndOffset)
          .find(!originalBuffer.charAt(_).isWhitespace)
          .getOrElse(originalEndOffset)
    }
  }

  override def advance(): Unit = {
    if (tokenEnd == originalEndOffset - 2 &&
      originalBuffer.subSequence(tokenEnd, originalEndOffset).toString == "*/") {
      myState = _ScalaDocMarkdownLexer.END
      offset = tokenEnd

      calcTokenEnd()
      return
    }

    myState match {
      case _ScalaDocMarkdownLexer.DELEGATE =>
        delegate.advance()
        offset = delegate.getTokenStart

        delegateState = delegate.getState
        // Check if we should switch to END state (2 characters left and they are "*/")
        if (delegate.getType == MarkdownTokenTypes.EOL) {
          myState = _ScalaDocMarkdownLexer.LINE_SPACE
        }
      case _ScalaDocMarkdownLexer.LINE_SPACE =>
        val nextChar = tokenEnd
        if (originalBuffer.length() > nextChar && originalBuffer.charAt(nextChar) == '*') {
          offset = nextChar
          myState = _ScalaDocMarkdownLexer.LEADING_ASTERISK
        } else {
          offset = nextChar
          if (!startNewMarkdownLine()) unpauseDelegate()
        }
      case _ScalaDocMarkdownLexer.LEADING_ASTERISK | _ScalaDocMarkdownLexer.WS_AFTER_LEADING_ASTERISK | _ScalaDocMarkdownLexer.START =>
        offset = tokenEnd
        if (!startNewMarkdownLine()) unpauseDelegate()
      case _ScalaDocMarkdownLexer.END => offset = originalEndOffset
    }
    calcTokenEnd()
  }

  override def getBufferSequence: CharSequence = originalBuffer

  override def getBufferEnd: Int = originalEndOffset
}

object _ScalaDocMarkdownLexer {
  private val START = 0
  private val DELEGATE = 1
  private val LINE_SPACE = 2
  private val LEADING_ASTERISK = 3
  private val WS_AFTER_LEADING_ASTERISK = 4
  private val END = 5

  private def isWhitespaceState(state: Int): Boolean = {
    state == LINE_SPACE ||
      state == WS_AFTER_LEADING_ASTERISK
  }

  // Note that some of these will never be produced by the lexer, but might as well still add them.
  private val DELEGATE_MAP = Map(
    MarkdownTokenTypes.TEXT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.CODE_LINE -> ScalaDocTokenType.DOC_INNER_CODE,
    MarkdownTokenTypes.BLOCK_QUOTE -> ScalaDocTokenType.DOC_BLOCKQUOTE,
    MarkdownTokenTypes.HTML_BLOCK_CONTENT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.SINGLE_QUOTE -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.DOUBLE_QUOTE -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.LPAREN -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.RPAREN -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.LBRACKET -> ScalaDocTokenType.DOC_LEFT_BRACKET,
    MarkdownTokenTypes.RBRACKET -> ScalaDocTokenType.DOC_RIGHT_BRACKET,
    MarkdownTokenTypes.LT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.GT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.COLON -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.EXCLAMATION_MARK -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.HARD_LINE_BREAK -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.EOL -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.LINK_ID -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.ATX_HEADER -> ScalaDocTokenType.DOC_MARKDOWN_HEADER,
    MarkdownTokenTypes.ATX_CONTENT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.SETEXT_1 -> ScalaDocTokenType.DOC_MARKDOWN_HEADER,
    MarkdownTokenTypes.SETEXT_2 -> ScalaDocTokenType.DOC_MARKDOWN_HEADER,
    MarkdownTokenTypes.SETEXT_CONTENT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.EMPH -> ScalaDocTokenType.DOC_MD_ASTERISKS,
    MarkdownTokenTypes.BACKTICK -> ScalaDocTokenType.DOC_MONOSPACE_TAG,
    MarkdownTokenTypes.ESCAPED_BACKTICKS -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.LIST_BULLET -> ScalaDocTokenType.DOC_LIST_ITEM_HEAD,
    MarkdownTokenTypes.URL -> ScalaDocTokenType.DOC_HTTP_LINK_VALUE,
    MarkdownTokenTypes.HORIZONTAL_RULE -> ScalaDocTokenType.DOC_HORIZONTAL_RULE,
    MarkdownTokenTypes.LIST_NUMBER -> ScalaDocTokenType.DOC_LIST_ITEM_HEAD,
    MarkdownTokenTypes.FENCE_LANG -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.CODE_FENCE_START -> ScalaDocTokenType.DOC_INNER_CODE_TAG,
    MarkdownTokenTypes.CODE_FENCE_CONTENT -> ScalaDocTokenType.DOC_COMMENT_DATA, // Unsure
    MarkdownTokenTypes.CODE_FENCE_END -> ScalaDocTokenType.DOC_INNER_CODE_TAG,
    MarkdownTokenTypes.LINK_TITLE -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.AUTOLINK -> ScalaDocTokenType.DOC_AUTOLINK,
    MarkdownTokenTypes.EMAIL_AUTOLINK -> ScalaDocTokenType.DOC_AUTOLINK,
    MarkdownTokenTypes.HTML_TAG -> ScalaDocTokenType.DOC_HTML_TAG,
    MarkdownTokenTypes.BAD_CHARACTER -> ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER,
    MarkdownTokenTypes.WHITE_SPACE -> ScalaDocTokenType.DOC_WHITESPACE,
  )
}