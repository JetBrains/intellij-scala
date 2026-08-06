package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.editor.{DocumentExt, EditorExt}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.util.MultilineStringUtil.MultilineQuotes
import org.jetbrains.plugins.scala.util.{MultilineStringSettings, MultilineStringUtil}

// TODO: add Scala prefix for all handlers for easy debug
class MultilineStringEnterHandler extends EnterHandlerDelegateAdapter {
  private var wasInMultilineString: Boolean = false
  private var whiteSpaceAfterCaret: String = ""

  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {

    val caretOffset = caretOffsetRef.get.intValue

    if (!file.is[ScalaFile] || !editor.inScalaString(caretOffset)) return Result.Continue

    val document = editor.getDocument
    val text = document.getImmutableCharSequence

    if (caretOffset == 0 || caretOffset >= text.length()) return Result.Continue
    val element = file.findElementAt(caretOffset)
    if (element == null) return Result.Continue

    val isInMultilineString = element.getParent match {
      case literal: ScStringLiteral => literal.isMultiLineString
      case _ => false
    }

    if (!isInMultilineString)
      return Result.Continue

    wasInMultilineString = true
    whiteSpaceAfterCaret = whitespaceAfter(text, caretOffset)
    document.deleteString(caretOffset, caretOffset + whiteSpaceAfterCaret.length)

    val caretBetweenBrackets = {
      val ch1 = text.charAt(caretOffset - 1)
      val ch2 = text.charAt(caretOffset)
      val caretBetweenParens = ch1 == '(' && ch2 == ')'
      val caretBetweenBraces = ch1 == '{' && ch2 == '}'
      caretBetweenParens || caretBetweenBraces
    }

    if (caretBetweenBrackets && CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER) {
      originalHandler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)
      Result.DefaultForceIndent
    } else {
      Result.Continue
    }
  }

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!file.is[ScalaFile]) return Result.Continue

    if (!wasInMultilineString) return Result.Continue
    wasInMultilineString = false

    val project = file.getProject
    val document = editor.getDocument
    document.commit(project) // TODO: AVOID COMMITTING DOCUMENTS ON TYPING!

    val caretModel = editor.getCaretModel
    val offset = caretModel.getOffset
    val caretMarker = document.createRangeMarker(offset, offset)
    caretMarker.setGreedyToRight(true)
    def caretOffset = caretMarker.getEndOffset

    val element = file.findElementAt(offset)
    if (element == null) return Result.Continue

    val literal: ScLiteral = MultilineStringUtil.findParentMLString(element) match {
      case Some(v) => v
      case _ => return Result.Continue
    }

    val literalOffset: Int = literal.getTextRange.getStartOffset
    val interpolRef: String = MultilineStringUtil.interpolatorPrefix(literal)
    val firstMLQuote: String = interpolRef + MultilineQuotes
    val firstMLQuoteLength: Int = firstMLQuote.length

    val settings = new MultilineStringSettings(project)
    import settings._

    if (!settings.supportMultilineString || offset - literalOffset < firstMLQuoteLength)
      return Result.Continue

    def getLineByNumber(number: Int): String = {
      val sequence = document.getImmutableCharSequence
      val start = document.getLineStartOffset(number)
      val end = document.getLineEndOffset(number)
      sequence.substring(start, end)
    }

    def insertNewLine(nlOffset: Int, indent: Int, trimPreviousLine: Boolean,
                      marginChar: Option[Char] = None): Unit = {
      document.insertString(nlOffset, "\n")
      forceIndent(nlOffset + 1, indent, marginChar)
      if (trimPreviousLine) {
        val line = getLineByNumber(document.getLineNumber(nlOffset))
        var i = 0
        def charToCheck = line.charAt(line.length - 1 - i)
        while (i <= line.length - 1 && (charToCheck == ' ' || charToCheck == '\t')) {
          i += 1
        }
        document.deleteString(nlOffset - i, nlOffset)
      }
    }

    def forceIndent(offset: Int, indent: Int, marginChar: Option[Char]): Unit = {
      val lineNumber = document.getLineNumber(offset)
      val lineStart = document.getLineStartOffset(lineNumber)
      val line = getLineByNumber(lineNumber)
      val wsPrefix = line.takeWhile(c => c == ' ' || c == '\t')
      document.replaceString(lineStart, lineStart + wsPrefix.length, getSmartSpaces(indent) + marginChar.getOrElse(""))
    }

    inWriteAction {
      val currentLineNumber = document.getLineNumber(offset)
      val prevLineNumber = currentLineNumber - 1
      val nextLineNumber = currentLineNumber + 1
      assert(prevLineNumber >= 0)

      val prevLine = getLineByNumber(prevLineNumber)
      val currentLine = getLineByNumber(prevLineNumber + 1)
      val nextLine = if (document.getLineCount > nextLineNumber) getLineByNumber(prevLineNumber + 2) else ""

      def prevLinePrefixAfterDelimiter(offsetInLine: Int): Int =
        StringUtils.substring(prevLine, offsetInLine).segmentLength(c => c == ' ' || c == '\t')

      val literalText = literal.getText
      val lines = literalText.split("\n")

      val marginChar: Char = MultilineStringUtil.getMarginChar(element)

      val marginCharOpt: Option[Char] = {
        if (settings.insertMargin && (
          lines.length > 3 ||
            MultilineStringUtil.hasMarginChars(element, marginChar.toString) ||
            MultilineStringUtil.needAddByType(literal))) {
          Some(marginChar)
        } else {
          None
        }
      }

      lazy val insertedBracketsOnSingleLine = lines.length == 3 && {
        def betweenBrackets = lines(0).endsWith('(') && lines(2).trim.startsWith(')')
        def betweenBraces = lines(0).endsWith('{') && lines(2).trim.startsWith('}')
        def caretIsBetweenBrackets = currentLineNumber == document.getLineNumber(literal.getTextRange.getStartOffset) + 1
        (betweenBrackets || betweenBraces) && caretIsBetweenBrackets
      }

      def handleEnterInsideMultilineExpanded(): Unit = {
        if (settings.insertMargin && MultilineStringUtil.needAddByType(literal)) {
          MultilineStringUtil.insertStripMargin(document, literal, marginChar)
        }

        val needNewLineBeforeLiteral = quotesOnNewLine && !literal.startsFromNewLine(false)
        if (needNewLineBeforeLiteral) {
          insertNewLine(literalOffset, 0, trimPreviousLine = true)
        }

        val manager = CodeStyleManager.getInstance(project)
        val newLinesAdded = insertedBracketsOnSingleLine.toInt + needNewLineBeforeLiteral.toInt

        manager.adjustLineIndent(document, document.getLineStartOffset(currentLineNumber))

        val firstLineIndent: Int = {
          val lineIdx = prevLineNumber + needNewLineBeforeLiteral.toInt
          val lineOffset = document.getLineStartOffset(lineIdx)
          val indentStr = manager.getLineIndent(document, lineOffset)
          calcIndentSize(indentStr)
        }

        val quotesIndent = firstLineIndent + interpolRef.length
        forceIndent(caretOffset, quotesIndent + marginIndent, marginCharOpt)
        if (insertedBracketsOnSingleLine) {
          forceIndent(caretOffset + 1, quotesIndent, marginCharOpt)
        }

        document.commit(project) // TODO: AVOID COMMITTING DOCUMENTS ON TYPING!

        if (settings.insertMargin) {
          for {
            lineIdx <- nextLineNumber to currentLineNumber + newLinesAdded
            if lineIdx < document.getLineCount
          } manager.adjustLineIndent(document, document.getLineStartOffset(lineIdx))
        }

        val closingQuotesOnNewLine =
          settings.closingQuotesOnNewLine && literalText.substring(offset - literalOffset) == MultilineQuotes

        if (closingQuotesOnNewLine) {
          caretMarker.setGreedyToRight(false)
          insertNewLine(caretOffset, quotesIndent, trimPreviousLine = false, marginCharOpt)
          caretMarker.setGreedyToRight(true)
          if (marginCharOpt.isDefined) {
            manager.adjustLineIndent(document, document.getLineStartOffset(currentLineNumber + newLinesAdded + 1))
          }
        }
      }

      def handleEnterInsideMultiline(): Unit = {
        val prevLineOffset = document.getLineStartOffset(prevLineNumber)
        val currentLineOffset = document.getLineStartOffset(currentLineNumber)

        val prevLineTrimmed = prevLine.trim
        val isPrevLineFirst = prevLineTrimmed.startsWith(firstMLQuote)

        val wsPrefixLength: Int = prevLine.segmentLength(c => c == ' ' || c == '\t')

        val quotesOptLength = if (isPrevLineFirst) firstMLQuoteLength else 0
        val prevLineStriped: String = {
          val idx = wsPrefixLength + quotesOptLength
          prevLine.substring(idx)
        }

        def handleEnterWithMargin(): Unit = {
          val currentLineHasMarginChar = currentLine.trim.startsWith(marginChar)
          if (currentLineHasMarginChar) return

          val inBraces = prevLine.endsWith('{') && nextLine.trim.startsWith('}') || prevLine.endsWith('(') && nextLine.trim.startsWith(')')

          val prefix: String = {
            if (inBraces)
              getPrefix(prevLine) + getSmartSpaces(quotesOptLength)
            else if (prevLineStriped.trim.startsWith(marginChar))
              getPrefix(prevLine) + getSmartSpaces(quotesOptLength)
            else if (nextLine.trim.startsWith(marginChar))
              getPrefix(nextLine)
            else
              getPrefix(currentLine)
          }

          val indentSizeAfterMargin: Int = {
            val offsetToContent =
              if (isPrevLineFirst) firstMLQuoteLength + prevLineStriped.startsWith(marginChar).toInt
              else 1
            prevLinePrefixAfterDelimiter(wsPrefixLength + offsetToContent)
          }

          forceIndent(caretOffset, getSmartLength(prefix), marginCharOpt)
          document.insertString(caretOffset, getSpaces(indentSizeAfterMargin))
          if (inBraces) {
            val nextLineOffset = document.getLineStartOffset(prevLineNumber + 2)
            forceIndent(nextLineOffset, 0, None)
            document.insertString(nextLineOffset, marginChar.toString + getSpaces(indentSizeAfterMargin))
            forceIndent(nextLineOffset, getSmartLength(prefix), None)
          }
        }

        def handleEnterWithoutMargin(): Unit = {
          val isCurrentLineEmpty = StringUtils.isBlank(currentLine)
          val isPrevLineEmpty = prevLine.trim.isEmpty

          if (prevLineOffset < literalOffset) {
            val beforeQuotes = prevLinePrefixAfterDelimiter(0)
            val elementStart = prevLine.indexOf(firstMLQuote) + firstMLQuoteLength
            val prevLineWsPrefixAfterQuotes = prevLinePrefixAfterDelimiter(elementStart)

            val spacesToInsert =
              if (isPrevLineFirst) {
                beforeQuotes + firstMLQuoteLength + prevLineWsPrefixAfterQuotes
              } else {
                val shiftLeft = if (isCurrentLineEmpty) 0 else wsPrefixLength
                elementStart - shiftLeft + prevLineWsPrefixAfterQuotes
              }
            forceIndent(currentLineOffset, getSmartLength(getSmartSpaces(spacesToInsert)), None)
          }
          else if (isCurrentLineEmpty && !isPrevLineEmpty) {
            forceIndent(caretOffset, wsPrefixLength, None)
          }
          else if (isPrevLineEmpty) {
            forceIndent(caretOffset, prevLine.length, None)
          }
          else if (isPrevLineFirst) {
            val wsAfterQuotes = prevLinePrefixAfterDelimiter(wsPrefixLength + firstMLQuoteLength) + firstMLQuoteLength
            forceIndent(caretOffset, wsAfterQuotes, None)
          }
        }

        val literalAlreadyHasLineMargin: Boolean = {
          // first line can contain quotes, so check stripped content
          def prevLineHasMargin = prevLineStriped.startsWith(marginChar)
          def otherLinesHaveMargin = lines.exists(_.trim.startsWith(marginChar))
          prevLineHasMargin || otherLinesHaveMargin
        }
        if (literalAlreadyHasLineMargin && settings.insertMargin) {
          handleEnterWithMargin()
        } else {
          handleEnterWithoutMargin()
        }
      }

      val wasSingleLine = lines.length <= 2 || insertedBracketsOnSingleLine
      if (wasSingleLine) {
        handleEnterInsideMultilineExpanded()
      } else {
        handleEnterInsideMultiline()
      }
      document.insertString(caretOffset, whiteSpaceAfterCaret)

      caretModel.moveToOffset(caretOffset)
      caretMarker.dispose()
    }

    Result.Stop
  }

  private def whitespaceAfter(chars: CharSequence, offset: Int): String = {
    val iterator = Iterator.range(offset, chars.length() - 1).map(chars.charAt)
    iterator.takeWhile(c => c == ' ' || c == '\t').mkString
  }
}