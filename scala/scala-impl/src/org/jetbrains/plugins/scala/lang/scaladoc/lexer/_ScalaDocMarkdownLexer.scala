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

  // A weird hack to adapt the tokeState: ntypes without copy-pasting the full list and mapping from one to the other
  // We need the collection to not accidentally register the same token several times
  // This is kinda awful but it'll do for now.
  private def platformType(@Nullable tokenType: org.intellij.markdown.IElementType): IElementType = {
    if (tokenType == null) return null
    _ScalaDocMarkdownLexer.DELEGATE_MAP.getOrElse(tokenType, ScalaDocTokenType.DOC_COMMENT_DATA)
  }

  override def getTokenType: IElementType = myState match {
    case _ScalaDocMarkdownLexer.DELEGATE =>
      platformType(delegate.getType)
    case _ScalaDocMarkdownLexer.LINE_SPACE =>
      ScalaDocTokenType.DOC_WHITESPACE
    case _ScalaDocMarkdownLexer.LEADING_ASTERISK =>
      ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
    case _ScalaDocMarkdownLexer.START =>
      ScalaDocTokenType.DOC_COMMENT_START
    case _ScalaDocMarkdownLexer.END =>
      if (offset < originalEndOffset) ScalaDocTokenType.DOC_COMMENT_END else null // Over when we advance from the end.
    case _ScalaDocMarkdownLexer.AT_DIRECTIVE_WS_START =>
      ScalaDocTokenType.DOC_WHITESPACE
    case _ScalaDocMarkdownLexer.AT_DIRECTIVE =>
      ScalaDocTokenType.DOC_TAG_NAME
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
    // Check for @-directives.
    // First, find the first non-whitespace character after `offset`:
    val firstNonWhitespaceIndex = (offset until originalEndOffset)
      .find(!originalBuffer.charAt(_).isWhitespace)

    firstNonWhitespaceIndex.exists { index =>
      originalBuffer.charAt(index) == '@' && {
        val nextIndex = index + 1
        if (nextIndex < originalEndOffset && !originalBuffer.charAt(nextIndex).isWhitespace) {

          if (index > offset) {
            myState = _ScalaDocMarkdownLexer.AT_DIRECTIVE_WS_START
          } else {
            myState = _ScalaDocMarkdownLexer.AT_DIRECTIVE
          }

          true
        } else {
          false
        }
      }
    }
    // For now, we won't deal with values in @-directives at all
  }

  private def calcTokenEnd(): Unit = {
    tokenEnd = myState match {
      case _ScalaDocMarkdownLexer.DELEGATE => delegate.getTokenEnd
      case _ScalaDocMarkdownLexer.LINE_SPACE =>
        (offset until originalEndOffset)
          .find(!originalBuffer.charAt(_).isWhitespace)
          .getOrElse(originalEndOffset)
      case _ScalaDocMarkdownLexer.LEADING_ASTERISK =>
        offset + 1
      case _ScalaDocMarkdownLexer.START =>
        offset + 3 // /** is always 3 characters
      case _ScalaDocMarkdownLexer.END =>
        offset + 2 // "*/" is always 2 characters
      case _ScalaDocMarkdownLexer.AT_DIRECTIVE_WS_START =>
        (offset until originalEndOffset)
          .find(!originalBuffer.charAt(_).isWhitespace)
          .getOrElse(originalEndOffset)
      case _ScalaDocMarkdownLexer.AT_DIRECTIVE =>
        // Until the first whitespace character after the @.
        (offset + 1 until originalEndOffset)
          .find(originalBuffer.charAt(_).isWhitespace)
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
      case _ScalaDocMarkdownLexer.LEADING_ASTERISK =>
        offset = tokenEnd
        if (!startNewMarkdownLine()) unpauseDelegate()
      case _ScalaDocMarkdownLexer.START =>
        offset = tokenEnd
        if (!startNewMarkdownLine()) unpauseDelegate()
      case _ScalaDocMarkdownLexer.END => offset = originalEndOffset
      case _ScalaDocMarkdownLexer.AT_DIRECTIVE_WS_START =>
        offset = tokenEnd
        myState = _ScalaDocMarkdownLexer.AT_DIRECTIVE
      case _ScalaDocMarkdownLexer.AT_DIRECTIVE =>
        offset = tokenEnd
        unpauseDelegate()
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
  private val END = 4

  private val AT_DIRECTIVE_WS_START = 5
  private val AT_DIRECTIVE = 6
  // TODO
  private val AT_DIRECTIVE_VALUE = 7
  private val AT_DIRECTIVE_WS = 8

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
    MarkdownTokenTypes.ATX_HEADER -> ScalaDocTokenType.VALID_DOC_HEADER,
    MarkdownTokenTypes.ATX_CONTENT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.SETEXT_1 -> ScalaDocTokenType.VALID_DOC_HEADER,
    MarkdownTokenTypes.SETEXT_2 -> ScalaDocTokenType.VALID_DOC_HEADER,
    MarkdownTokenTypes.SETEXT_CONTENT -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.EMPH -> ScalaDocTokenType.DOC_MD_ASTERISKS,
    MarkdownTokenTypes.BACKTICK -> ScalaDocTokenType.DOC_MONOSPACE_TAG,
    MarkdownTokenTypes.ESCAPED_BACKTICKS -> ScalaDocTokenType.DOC_COMMENT_DATA,
    MarkdownTokenTypes.LIST_BULLET -> ScalaDocTokenType.DOC_LIST_ITEM_HEAD,
    MarkdownTokenTypes.URL -> ScalaDocTokenType.DOC_HTTP_LINK_VALUE,
    MarkdownTokenTypes.HORIZONTAL_RULE -> ScalaDocTokenType.DOC_HORIZONTAL_RULE,
    // TODO: Doesn't work; unsure why.
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