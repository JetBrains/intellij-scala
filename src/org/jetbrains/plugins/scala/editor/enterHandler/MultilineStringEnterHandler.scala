package org.jetbrains.plugins.scala
package editor.enterHandler

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.format.StringConcatenationParser
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.util.MultilineStringSettings
import org.jetbrains.plugins.scala.util.MultilineStringUtil._

/**
 * User: Dmitry Naydanov
 * Date: 2/27/12
 */

class MultilineStringEnterHandler extends EnterHandlerDelegateAdapter {
  private var wasInMultilineString: Boolean = false
  private var whiteSpaceAfterCaret: String = ""

  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer], caretAdvance: Ref[Integer], 
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    val document = editor.getDocument
    val text = document.getText
    val caretOffset = caretOffsetRef.get.intValue

    if (caretOffset == 0 || caretOffset >= text.length()) return Result.Continue

    val element = file findElementAt caretOffset
    if (!inMultilineString(element)) return Result.Continue
    else wasInMultilineString = true

    val ch1 = text.charAt(caretOffset - 1)
    val ch2 = text.charAt(caretOffset)

    whiteSpaceAfterCaret = text.substring(caretOffset).takeWhile(c => c == ' ' || c == '\t')
    document.deleteString(caretOffset, caretOffset + whiteSpaceAfterCaret.length)

    if ((ch1 != '(' || ch2 != ')')&&(ch1 != '{' || ch2 != '}') || !CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER)
    return Result.Continue

    originalHandler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)
    Result.DefaultForceIndent
  }

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.Continue

    val caretModel = editor.getCaretModel
    val document = editor.getDocument
    val offset = caretModel.getOffset
    val caretMarker = document.createRangeMarker(offset, offset)
    caretMarker.setGreedyToRight(true)
    def caretOffset = caretMarker.getEndOffset

    val project = file.getProject
    val element = file.findElementAt(offset)
    
    if (!wasInMultilineString) return Result.Continue
    wasInMultilineString = false

    val marginChar = getMarginChar(element)

    val settings = new MultilineStringSettings(project)
    import settings._

    val literal = findParentMLString(element).getOrElse(return Result.Continue)
    val literalOffset = literal.getTextRange.getStartOffset
    val firstMLQuote = interpolatorPrefix(literal) + multilineQuotes
    val firstMLQuoteLength = firstMLQuote.length

    if (supportLevel == ScalaCodeStyleSettings.MULTILINE_STRING_NONE || offset - literalOffset < firstMLQuoteLength) return Result.Continue

    def getLineByNumber(number: Int): String =
      document.getText(new TextRange(document.getLineStartOffset(number), document.getLineEndOffset(number)))
    
    def getSpaces(count: Int) = StringUtil.repeat(" ", count)
    
    def getSmartSpaces(count: Int) = if (useTabs) {
      StringUtil.repeat("\t", count/tabSize) + StringUtil.repeat(" ", count%tabSize)
    } else {
      StringUtil.repeat(" ", count)
    }
    
    def getSmartLength(line: String) = if (useTabs) line.length + line.count(_ == '\t')*(tabSize - 1) else line.length 
    
    def insertNewLine(nlOffset: Int, indent: Int, trimPreviousLine: Boolean) {
      document.insertString(nlOffset, "\n")
      forceIndent(nlOffset + 1, indent, None)
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

    def forceIndent(offset: Int, indent: Int, marginChar: Option[Char]) {
      val lineNumber = document.getLineNumber(offset)
      val lineStart = document.getLineStartOffset(lineNumber)
      val line = getLineByNumber(lineNumber)
      val wsPrefix = line.takeWhile(c => c == ' ' || c == '\t')
      document.replaceString(lineStart, lineStart + wsPrefix.length, getSmartSpaces(indent) + marginChar.getOrElse(""))
    }

    extensions inWriteAction {
      val prevLineNumber = document.getLineNumber(offset) - 1
      assert(prevLineNumber >= 0)
      val prevLine = getLineByNumber(prevLineNumber)
      val currentLine = getLineByNumber(prevLineNumber + 1)
      val nextLine = if (document.getLineCount > prevLineNumber + 2) getLineByNumber(prevLineNumber + 2) else ""

      def prevLinePrefixAfterDelimiter(offsetInLine: Int): Int =
        if (prevLine.length > offsetInLine) prevLine.substring(offsetInLine).prefixLength(c => c == ' ' || c == '\t') else 0

      val wasSingleLine = literal.getText.indexOf("\n") == literal.getText.lastIndexOf("\n")
      val lines = literal.getText.split("\n")

      val marginCharFromSettings = selectBySettings[Option[Char]](None)(Some(marginChar))
      val marginCharOpt =
        marginCharFromSettings match {
          case Some(mChar) if hasMarginChars(element, mChar.toString) ||
            (!hasMarginChars(element, mChar.toString) && lines.length > 3)  || needAddByType(literal) => marginCharFromSettings
          case _ => None
        }

      if (wasSingleLine || lines.length == 3 &&
      (lines(0).endsWith("(") && lines(2).trim.startsWith(")") || lines(0).endsWith("{") && lines(2).trim.startsWith("}"))) {
        val trimmedStartLine = getLineByNumber(document.getLineNumber(offset) - 1).trim()
        val inConcatenation = literal.getParent match {
          case ScInfixExpr(lit: ScLiteral, op, `literal`) if op.refName == "+" && lit.isString => Option(lit)
          case ScInfixExpr(expr, op, `literal`) if op.refName == "+" && StringConcatenationParser.isString(expr) => Option(expr)
          case _ => None
        }
        val needInsertNLBefore = (!trimmedStartLine.startsWith(firstMLQuote) || inConcatenation.isDefined) && quotesOnNewLine

        selectBySettings()(if (needAddByType(literal)) insertStripMargin(document, literal, marginChar))

        val prevIndent =
          if (inConcatenation.isDefined) inConcatenation.map { expr =>
            val exprStart = expr.getTextRange.getStartOffset
            val lineStart = document.getLineStartOffset(document.getLineNumber(exprStart))
            getSmartLength(document.getText.substring(lineStart, exprStart))
          }.get
          else prefixLength(prevLine)

        val needInsertIndentInt =
          if (needInsertNLBefore && inConcatenation.isEmpty) regularIndent
          else 0

        if (needInsertNLBefore) {
          insertNewLine(literalOffset, prevIndent + needInsertIndentInt, trimPreviousLine = true)
        }

        val indentSize = prevIndent + needInsertIndentInt + interpolatorPrefixLength(literal) + marginIndent

        if (literal.getText.substring(offset - literalOffset) == multilineQuotes) {
          forceIndent(caretOffset, indentSize, marginCharOpt)
          caretMarker.setGreedyToRight(false)
          insertNewLine(caretOffset, indentSize - marginIndent, trimPreviousLine = false)
          caretMarker.setGreedyToRight(true)
        } else {
          forceIndent(caretOffset, indentSize, marginCharOpt)
        }

        if (!wasSingleLine) {
          val currentPrefix = getPrefix(getLineByNumber(document.getLineNumber(caretOffset)))
          forceIndent(caretOffset + 1, getSmartLength(currentPrefix), marginCharOpt)
        }
      } else {
        val isCurrentLineEmpty = currentLine.trim.length == 0
        val currentLineOffset = document.getLineStartOffset(prevLineNumber + 1)

        val isPrevLineFirst = prevLine startsWith firstMLQuote
        val isPrevLineTrimmedFirst = prevLine.trim startsWith firstMLQuote
        val prevLineStartOffset = document getLineStartOffset prevLineNumber

        val wsPrefix =
          if (isPrevLineFirst) prevLinePrefixAfterDelimiter(firstMLQuoteLength) + firstMLQuoteLength
          else prevLine.prefixLength(c => c == ' ' || c == '\t')

        val prefixStriped = prevLine.substring(wsPrefix)
        
        if (supportLevel == ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT ||
          !prefixStriped.startsWith(Seq(marginChar)) && !prefixStriped.startsWith(firstMLQuote) ||
                !lines.map(_.trim).exists(_.startsWith(Seq(marginChar)))) {
          if (prevLineStartOffset < literalOffset) {
            val beforeQuotes = prevLinePrefixAfterDelimiter(0)
            val elementStart = prevLine.indexOf(firstMLQuote) + firstMLQuoteLength
            val prevLineWsPrefixAfterQuotes = prevLinePrefixAfterDelimiter(elementStart)

            val spacesToInsert =
              if (isPrevLineTrimmedFirst) beforeQuotes + firstMLQuoteLength + prevLineWsPrefixAfterQuotes
              else (if (isCurrentLineEmpty) elementStart else elementStart - wsPrefix) + prevLineWsPrefixAfterQuotes
            forceIndent(currentLineOffset, getSmartLength(getSmartSpaces(spacesToInsert)), None)
          }
          else if (isCurrentLineEmpty && prevLine.length > 0)
            forceIndent(caretOffset, wsPrefix, None)
          else if (prevLine.trim.length == 0)
            forceIndent(caretOffset, prevLine.length, None)
          else if (isPrevLineTrimmedFirst) {
            val wsAfterQuotes = prevLinePrefixAfterDelimiter(wsPrefix + firstMLQuoteLength) + firstMLQuoteLength
            forceIndent(caretOffset, wsAfterQuotes, None)
          }
        } else {
          val wsAfterMargin =
            if (isPrevLineFirst) firstMLQuoteLength else prevLinePrefixAfterDelimiter(wsPrefix + 1)

          if (!currentLine.trim.startsWith(Seq(marginChar))) {
            val inBraces = prevLine.endsWith("{") && nextLine.trim.startsWith("}") || prevLine.endsWith("(") && nextLine.trim.startsWith(")")
            val prefix =
              if (inBraces) getPrefix(nextLine)
              else if (prevLine.trim.startsWith(Seq(marginChar))) getPrefix(prevLine)
              else if (nextLine.trim.startsWith(Seq(marginChar))) getPrefix(nextLine)
              else getPrefix(currentLine)

            forceIndent(caretOffset, getSmartLength(prefix), marginCharOpt)
            document.insertString(caretOffset, getSpaces(wsAfterMargin))
            if (inBraces) {
              val nextLineOffset = document.getLineStartOffset(prevLineNumber + 2)
              forceIndent(nextLineOffset, 0, None)
              document.insertString(nextLineOffset, marginChar + getSpaces(wsAfterMargin))
              forceIndent(nextLineOffset, getSmartLength(prefix), None)
            }
          }
        }
      }
      document.insertString(caretOffset, whiteSpaceAfterCaret)

      caretModel.moveToOffset(caretOffset)
      caretMarker.dispose()
    }

    Result.Stop
  }
}