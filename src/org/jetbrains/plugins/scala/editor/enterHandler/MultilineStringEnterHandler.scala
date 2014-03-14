package org.jetbrains.plugins.scala
package editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import lang.psi.api.ScalaFile
import lang.lexer.ScalaTokenTypes
import lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReferenceElement, ScLiteral}
import com.intellij.psi.{PsiElement, PsiDocumentManager, PsiFile}
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import lang.psi.api.expr._
import collection.mutable.ArrayBuffer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.extensions.toPsiElementExt
import MultilineStringEnterHandler._
import org.jetbrains.plugins.scala.format.StringConcatenationParser

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

    originalHandler.execute(editor, dataContext)
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

    val settings = CodeStyleSettingsManager.getInstance(element.getProject).getCurrentSettings
    val scalaSettings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
    
    val marginChar = getMarginChar(element)
    val useTabs = settings useTabCharacter ScalaFileType.SCALA_FILE_TYPE
    val tabSize = settings getTabSize ScalaFileType.SCALA_FILE_TYPE
    val mlIndentSize = scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT

    val literal = findParentMLString(element).getOrElse(return Result.Continue)
    val literalOffset = literal.getTextRange.getStartOffset
    val firstMLQuote = interpolatorPrefix(literal) + multilineQuotes
    val firstMLQuoteLength = firstMLQuote.length

    if (scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_NONE ||
      offset - literalOffset < firstMLQuoteLength) return Result.Continue


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

    def selectBySettings[T](ifIndent: => T)(ifAll: => T): T = {
      scalaSettings.MULTILINE_STRING_SUPORT match {
        case ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT => ifIndent
        case ScalaCodeStyleSettings.MULTILINE_STRING_ALL => ifAll
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

      def prefixLength(line: String) = if (useTabs) {
        val tabsCount = line prefixLength (_ == '\t')
        tabsCount*tabSize + line.substring(tabsCount).prefixLength(_ == ' ')
      } else {
        line prefixLength (_ == ' ')
      }
      def prevLinePrefixAfterDelimiter(offsetInLine: Int): Int =
        if (prevLine.length > offsetInLine) prevLine.substring(offsetInLine).prefixLength(c => c == ' ' || c == '\t') else 0

      def getPrefix(line: String) = getSmartSpaces(prefixLength(line))
      def insertStripMargin() {
        if (needAddStripMargin(element, "" + marginChar)) {
          document.insertString(literal.getTextRange.getEndOffset,
            if (marginChar == '|') ".stripMargin" else ".stripMargin(\'" + marginChar + "\')")
        }
      }

      val wasSingleLine = literal.getText.indexOf("\n") == literal.getText.lastIndexOf("\n")
      val lines = literal.getText.split("\n")

      val marginCharOpt = selectBySettings[Option[Char]](None)(Some(marginChar))

      if (wasSingleLine || lines.length == 3 &&
      (lines(0).endsWith("(") && lines(2).trim.startsWith(")") || lines(0).endsWith("{") && lines(2).trim.startsWith("}"))) {
        val trimmedStartLine = getLineByNumber(document.getLineNumber(offset) - 1).trim()
        val inConcatenation = literal.getParent match {
          case ScInfixExpr(lit: ScLiteral, op, `literal`) if op.refName == "+" && lit.isString => Option(lit)
          case ScInfixExpr(expr, op, `literal`) if op.refName == "+" && StringConcatenationParser.isString(expr) => Option(expr)
          case _ => None
        }
        val needInsertNLBefore =
          (!trimmedStartLine.startsWith(firstMLQuote) || inConcatenation.isDefined) &&
            scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE

        selectBySettings()(insertStripMargin())

        val prevIndent =
          if (inConcatenation.isDefined) inConcatenation.map { expr =>
            val exprStart = expr.getTextRange.getStartOffset
            val lineStart = document.getLineStartOffset(document.getLineNumber(exprStart))
            getSmartLength(document.getText.substring(lineStart, exprStart))
          }.get
          else prefixLength(prevLine)

        val needInsertIndentInt =
          if (needInsertNLBefore && !inConcatenation.isDefined) settings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).INDENT_SIZE
          else 0

        if (needInsertNLBefore) {
          insertNewLine(literalOffset, prevIndent + needInsertIndentInt, trimPreviousLine = true)
        }

        val indentSize = prevIndent + needInsertIndentInt + interpolatorPrefixLength(literal) + mlIndentSize

        if (literal.getText.substring(offset - literalOffset) == multilineQuotes) {
          forceIndent(caretOffset, indentSize, marginCharOpt)
          caretMarker.setGreedyToRight(false)
          insertNewLine(caretOffset, indentSize - mlIndentSize, trimPreviousLine = false)
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
        
        if (scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT ||
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

object MultilineStringEnterHandler {
  val multilineQuotes = "\"\"\""
  val multilineQuotesLength = multilineQuotes.length

  def inMultilineString(element: PsiElement): Boolean = {
    if (element == null) return false
    element.getNode.getElementType match {
      case ScalaTokenTypes.tMULTILINE_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING => true
      case ScalaTokenTypes.tINTERPOLATED_STRING_END | ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION |
           ScalaTokenTypes.tINTERPOLATED_STRING_ESCAPE =>
        element.getParent match {
          case lit: ScLiteral if lit.isMultiLineString => true
          case _ => false
        }
      case _ => false
    }
  }

  def needAddMethodCallToMLString(stringElement: PsiElement, methodName: String): Boolean = {
    var parent = stringElement.getParent

    do {
      parent match {
        case ref: ScReferenceElement => //if (ref.nameId.getText == methodName) return false
        case l: ScLiteral => if (!l.isMultiLineString) return false
        case i: ScInfixExpr => if (i.operation.getText == methodName) return false
        case call: ScMethodCall =>
          if (Option(call.getEffectiveInvokedExpr).forall {
            case expr: ScExpression => expr.getText endsWith "." + methodName
            case _ => false
          }) return false
        case _: ScParenthesisedExpr =>
        case _ => return true
      }

      parent = parent.getParent
    } while (parent != null)

    true
  }

  def needAddStripMargin(element: PsiElement, marginChar: String) = {
    def hasMarginChars(element: PsiElement) = element.getText.replace("\r", "").split("\n[ \t]*\\|").length > 1

    findAllMethodCallsOnMLString(element, "stripMargin").isEmpty && !hasMarginChars(element)
  }

  def getMarginChar(element: PsiElement): Char = {
    val calls = findAllMethodCallsOnMLString(element, "stripMargin")
    val defaultMargin = CodeStyleSettingsManager.getInstance(element.getProject).getCurrentSettings.
      getCustomSettings(classOf[ScalaCodeStyleSettings]).MARGIN_CHAR


    if (calls.isEmpty) return defaultMargin

    calls.apply(0).headOption match {
      case Some(ScLiteral(c: Character)) => c
      case _ => defaultMargin
    }
  }

  def findAllMethodCallsOnMLString(stringElement: PsiElement, methodName: String): Array[Array[ScExpression]] = {
    val calls = new ArrayBuffer[Array[ScExpression]]()
    def callsArray = calls.toArray

    var prevParent: PsiElement = findParentMLString(stringElement).getOrElse(return Array.empty)
    var parent = prevParent.getParent

    do {
      parent match {
        case lit: ScLiteral => if (!lit.isMultiLineString) return Array.empty
        case inf: ScInfixExpr =>
          if (inf.operation.getText == methodName){
            if (prevParent != parent.getFirstChild) return callsArray
            calls += Array(inf.rOp)
          }
        case call: ScMethodCall =>
          call.getEffectiveInvokedExpr match {
            case ref: ScReferenceExpression if ref.refName == methodName => calls += call.args.exprsArray
            case _ =>
          }
        case exp: ScReferenceExpression =>
          if (!exp.getParent.isInstanceOf[ScMethodCall]) {
            calls += Array[ScExpression]()
          }
        case _: ScParenthesisedExpr =>
        case _ => return callsArray
      }

      prevParent = parent
      parent = parent.getParent
    } while (parent != null)

    callsArray
  }

  def findParentMLString(element: PsiElement): Option[ScLiteral] = {
    (Iterator(element) ++ element.parentsInFile).collect {
      case lit: ScLiteral if lit.isMultiLineString => lit
    }.toStream.headOption
  }

  def isMLString(element: PsiElement): Boolean = element match {
    case lit: ScLiteral if lit.isMultiLineString => true
    case _ => false
  }

  def interpolatorPrefixLength(literal: ScLiteral) = interpolatorPrefix(literal).length

  def interpolatorPrefix(literal: ScLiteral) = literal match {
    case isl: ScInterpolatedStringLiteral if isl.reference.isDefined => isl.reference.get.refName
    case _ => ""
  }

  def containsArgs(currentArgs: Array[Array[ScExpression]], argsToFind: String*): Boolean = {
    val myArgs = argsToFind.sorted

    for (arg <- currentArgs) {
      val argsString = arg.map(_.getText).sorted

      if (myArgs.sameElements(argsString) || myArgs.reverse.sameElements(argsString)) return true
    }

    false
  }

}
