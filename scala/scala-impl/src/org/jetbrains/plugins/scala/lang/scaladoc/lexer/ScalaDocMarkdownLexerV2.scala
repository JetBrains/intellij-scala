package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.ScalaDocMarkdownFlavour

import scala.collection.immutable.ArraySeq

// TODO: document
class MarkdownWrapperType(val delegate: org.intellij.markdown.IElementType) extends ScalaDocElementType(delegate.getName)

// TODO: Lexing changes for @-directives
//       This requires detecting @-directives after newlines, which is a bit of a pain
//       Possibly it is better to turn this into a flex lexer, but I'm not sure
//       @-directives require adding a state for @, and a state for lexing words and such
//       which is a lot of logic with some state transfer and stuff. painful.
//       might be better to lex the entire "special bit" of the directive in one go, producing a list of tokens, that we then queue down?
//       decent idea.
class ScalaDocMarkdownLexerV2 extends LexerBase {
  private val interiorLexer = (new ScalaDocMarkdownFlavour).createInlinesLexer()
  private var originalBuffer: CharSequence = _
  private var originalStartOffset: Int = _
  private var originalEndOffset: Int = _

  private var filteredBuffer: String = _
  private var filteredBufferMap: Seq[Int] = _

  private var currentLine: Int = 0
  private var myState: Int = State.START

  private object State {
    val INTERIOR_PASSTHROUGH = 0
    val EOL_WHITESPACE = 1
    val LEADING_ASTERISKS = 2
    val START = 3
    val END = 4
  }

  override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
    originalBuffer = buffer
    originalStartOffset = startOffset
    originalEndOffset = endOffset

    val text = buffer.subSequence(startOffset, endOffset).toString
    val (content, map) = splitContext(text)
    filteredBuffer = content
    filteredBufferMap = map

    currentLine = 0

    myState = if (text.startsWith("/*")) State.START else State.INTERIOR_PASSTHROUGH

    interiorLexer.start(filteredBuffer, 0, filteredBuffer.length, initialState)
  }

  // TODO: needs to match behavior from the parser.
  private def splitContext(text: String): (String, Seq[Int]) = {
    val initialOffset = if (text.startsWith("/*")) 2 else 0

    val content = text.substring(
      initialOffset,
      if (text.endsWith("*/")) text.length - 2 else text.length
    )

    var extraRemoved = initialOffset
    val result = content.linesWithSeparators.map(line => {
      val initialLength = line.length
      val trimmed = line.stripLeading

      val cleanedLine = if (!trimmed.startsWith("*")) {
        trimmed
      } else {
        trimmed.substring(1)
      }

      // Don't fully delete empty lines, keep the newline.
      val finalLine = if (cleanedLine.isEmpty) "\n" else cleanedLine

      extraRemoved += initialLength - finalLine.length

      (finalLine, extraRemoved)
    })

    // Technically not very efficient, but meh. We need to collect both at once.
    // A `collect` would work but would be more manual.
    val lines = new StringBuilder
    val map = ArraySeq.newBuilder[Int]

    result.foreach { case (line, spacing) =>
      lines.append(line)
      map += spacing
    }
    map += extraRemoved

    (lines.result(), map.result())
  }

  private def platformType(@Nullable tokenType: org.intellij.markdown.IElementType): IElementType = {
    if (tokenType == null) return null
    new MarkdownWrapperType(tokenType)
  }

  override def getState: Int =
    (interiorLexer.getState << 3) + myState

  override def getTokenType: IElementType = myState match {
    case State.INTERIOR_PASSTHROUGH => platformType(interiorLexer.getType)
    case State.EOL_WHITESPACE => ScalaDocTokenType.DOC_WHITESPACE
    case State.LEADING_ASTERISKS => ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
    case State.START => ScalaDocTokenType.DOC_COMMENT_START
    case State.END => ScalaDocTokenType.DOC_COMMENT_END
  }

  // For both EOL_WHITESPACE and LEADING_ASTERISKS, we can probably get that info from `splitContext`.
  override def getTokenStart: Int = originalStartOffset + (myState match {
    case State.INTERIOR_PASSTHROUGH => filteredBufferMap(currentLine) + interiorLexer.getTokenStart
    case State.START => 0
    case State.END => return originalEndOffset - 2
    case State.EOL_WHITESPACE => filteredBufferMap(currentLine) + interiorLexer.getTokenStart
    // Leading asterisks are right at the end of EOL_WHITESPACE
    case State.LEADING_ASTERISKS => filteredBufferMap(currentLine) + interiorLexer.getTokenEnd - 1
  })

  override def getTokenEnd: Int = originalStartOffset + (myState match {
    case State.INTERIOR_PASSTHROUGH => filteredBufferMap(currentLine) + interiorLexer.getTokenEnd
    case State.START => 2
    case State.END => return originalEndOffset
    case State.EOL_WHITESPACE => filteredBufferMap(currentLine + 1) + interiorLexer.getTokenEnd - 1
    case State.LEADING_ASTERISKS => filteredBufferMap(currentLine) + interiorLexer.getTokenEnd
  })

  override def advance(): Unit = myState match {
    case State.INTERIOR_PASSTHROUGH =>
      interiorLexer.advance()
      if (interiorLexer.getType == MarkdownTokenTypes.EOL) {
        myState = State.EOL_WHITESPACE
      }
    case State.EOL_WHITESPACE =>
      if (originalBuffer.charAt(getTokenEnd) == '*') {
        myState = State.LEADING_ASTERISKS
      } else {
        myState = State.INTERIOR_PASSTHROUGH
        advance() // Advance, because it's at EOL before.
      }
      currentLine += 1
    case State.LEADING_ASTERISKS =>
      myState = State.INTERIOR_PASSTHROUGH
      advance() // Advance, because it's at EOL before.
    case State.START =>
      if (interiorLexer.getType == MarkdownTokenTypes.EOL) {
        myState = State.EOL_WHITESPACE
      } else {
        myState = State.INTERIOR_PASSTHROUGH
      }
    case State.END => // Do nothing, we're already done. (Maybe? Double check, maybe we need to go into a "final" state)
  }

  override def getBufferSequence: CharSequence = originalBuffer

  override def getBufferEnd: Int = originalEndOffset
}
