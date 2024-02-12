package org.jetbrains.plugins.scala.util

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format.WithStrippedMargin
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, literals}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

import java.util.regex.Pattern

object MultilineStringUtil {

  val MultilineQuotes = "\"\"\""
  val MultilineQuotesLength: Int = MultilineQuotes.length
  val DefaultMarginChar = '|'

  private val escaper = Pattern.compile("([^a-zA-z0-9])")

  def escapeForRegexp(s: String): String =
    escaper.matcher(s).replaceAll("\\\\$1")

  def hasMarginChars(element: PsiElement, marginChar: String): Boolean = {
    element.getText.replace("\r", "").split(s"\n[ \t]*${escapeForRegexp(marginChar)}").length > 1
  }

  def hasStripMarginCall(element: PsiElement): Boolean = {
    val calls = findAllMethodCallsOnMLString(element, "stripMargin")
    calls.nonEmpty
  }

  /** @return true<br>
   *          1) if multiline string literal has stripMargin on the call site and one of these is true:<br>
   *          1.1) it is one-line multiline string<br>
   *          1.2) at least one non-empty-line has margin<br>
   *          2) if multiline string literal does not have stripMargin and all non-empty lines start with a margin<br>
   *          <br>
   *          false  otherwise
   */
  def looksLikeUsesMargins(literal: ScLiteral): Boolean = {
    val text = literal.contentText
    val lines = text.linesIterator
      .map(_.trim)
      .filterNot(_.isEmpty)

    literal match {
      case WithStrippedMargin(_, marginChar) =>
        val isOneLine = !literal.textContains('\n')
        isOneLine || lines.isEmpty || lines.exists(_.startsWith(marginChar))
      case _ =>
        val withoutFirst = lines.drop(1)
        withoutFirst.hasNext && withoutFirst.forall(_.startsWith("|"))
    }
  }

  def needAddByType(literal: ScLiteral): Boolean = literal match {
    case ScInterpolatedStringLiteral(ResolvesTo(funDef: ScFunction)) =>
      funDef.returnType
        .map(_.canonicalText)
        .exists { cannonicalText =>
          cannonicalText.endsWith("java.lang.String") ||
            cannonicalText.endsWith("scala.Predef.String")
        }
    case _ => true
  }

  def insertStripMargin(document: Document, literal: ScLiteral, marginChar: Char): Unit = {
    if (!hasStripMarginCall(literal) && !hasMarginChars(literal, marginChar.toString)) {
      val stripText = if (marginChar == DefaultMarginChar) ".stripMargin"
      else s".stripMargin('$marginChar')"
      document.insertString(literal.getTextRange.getEndOffset, stripText)
    }
  }

  def getMarginChar(element: PsiElement): Char = {
    val char = detectMarginChar(element)
    char.getOrElse {
      val scalaCodeStyle = CodeStyle.getSettings(element.getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
      scalaCodeStyle.getMarginChar
    }
  }

  def detectMarginChar(element: PsiElement): Option[Char] = {
    val stripMarginCall = findAllMethodCallsOnMLString(element, "stripMargin")
    stripMarginCall match {
      case Seq(Seq(literals.ScCharLiteral(value), _*), _*) => //custom margin char: `"""...""".stripMargin('#')`
        Some(value)
      case Seq(Seq(), _*) =>
        Some(DefaultMarginChar)
      case _ =>
        None
    }
  }

  def findAllMethodCallsOnMLString(stringElement: PsiElement, methodName: String): Seq[Seq[ScExpression]] = {
    val calls = Seq.newBuilder[Seq[ScExpression]]

    var prevParent: PsiElement = findParentMLString(stringElement).getOrElse(return Seq.empty)
    var parent = prevParent.getParent

    do {
      parent match {
        case lit: ScLiteral =>
          if (!lit.isMultiLineString)
            return Seq.empty
        case inf: ScInfixExpr =>
          if (inf.operation.textMatches(methodName)) {
            if (prevParent != parent.getFirstChild)
              return calls.result()
            calls += Seq(inf.right)
          }
        case postfix: ScPostfixExpr =>
          if (postfix.operation.textMatches(methodName)) {
            if (prevParent != parent.getFirstChild)
              return calls.result()
            calls += Seq.empty
          }
        case call: ScMethodCall =>
          call.getEffectiveInvokedExpr match {
            case ref: ScReferenceExpression if ref.refName == methodName =>
              calls += call.args.exprs
            case _ =>
          }
        case exp: ScReferenceExpression =>
          if (!exp.getParent.is[ScMethodCall]) {
            calls += Seq.empty
          }
        case _: ScParenthesisedExpr =>
        case _ =>
          return calls.result()
      }

      prevParent = parent
      parent = parent.getParent
    } while (parent != null)

    calls.result()
  }

  def findParentMLString(element: PsiElement): Option[ScLiteral] = {
    element.withParentsInFile.collect {
      case lit: ScLiteral if lit.isMultiLineString => lit
    }.to(LazyList).headOption
  }

  def interpolatorPrefixLength(literal: ScLiteral): Int = interpolatorPrefix(literal).length

  def interpolatorPrefix(literal: ScLiteral): String = literal match {
    case stringLiteral: ScInterpolatedStringLiteral => stringLiteral.referenceName
    case _ => ""
  }

  def containsArgs(currentArgs: Seq[Seq[ScExpression]], argsToFind: String*): Boolean = {
    val myArgs = argsToFind.sorted

    for (arg <- currentArgs) {
      val argsString = arg.map(_.getText).sorted

      if (myArgs == argsString || myArgs.reverse == argsString) return true
    }

    false
  }

  //TODO: it seems like it only works when the original string literal doesn't have ".stripMargin" call
  def addMarginsAndFormatMLString(literal: ScStringLiteral, document: Document, caretOffset: Int = 0): Int = {
    addMarginsAndFormatMLString(literal, document, getMarginChar(literal), caretOffset)
  }

  /**
   * @param literal     multiline string literal element that needs margins
   * @param document    document containing element
   * @param _marginChar  margin char to use
   * @param caretOffset current caret offset inside document
   * @return additional caret offset needed to preserve original caret position relative to surrounding string parts
   */
  def addMarginsAndFormatMLString(
    literal: ScStringLiteral,
    document: Document,
    _marginChar: => Char,
    caretOffset: Int
  ): Int = {
    val settings = new MultilineStringSettings(literal.getProject)
    if (settings.insertMargin)
      addMarginsAndFormatMLStringWithoutCheck(literal, document, _marginChar, caretOffset, settings)
    else
      0
  }

  def addMarginsAndFormatMLStringWithoutCheck(
    literal: ScStringLiteral,
    document: Document,
    _marginChar: => Char,
    caretOffset: Int
  ): Int = {
    val settings = new MultilineStringSettings(literal.getProject)
    addMarginsAndFormatMLStringWithoutCheck(literal, document, _marginChar, caretOffset, settings)
  }

  private def addMarginsAndFormatMLStringWithoutCheck(
    literal: ScStringLiteral,
    document: Document,
    _marginChar: => Char,
    caretOffset: Int,
    settings: MultilineStringSettings
  ): Int = {
    if (!literal.isMultiLineString)
      throw new IllegalStateException(s"Need multiline string literal, but get: ${literal.getText}")

    PsiDocumentManager.getInstance(literal.getProject).doPostponedOperationsAndUnblockDocument(document)

    val firstMLQuote = interpolatorPrefix(literal) + MultilineQuotes

    val (literalStart, literalEnd) = (literal.getTextRange.getStartOffset, literal.getTextRange.getEndOffset)
    val (startLineNumber, endLineNumber) = (document.getLineNumber(literalStart), document.getLineNumber(literalEnd))
    val (startLineOffset, startLineEndOffset) = (document.getLineStartOffset(startLineNumber), document.getLineEndOffset(startLineNumber))

    val text = document.getImmutableCharSequence
    val startLine = text.substring(startLineOffset, startLineEndOffset)
    val startsOnNewLine = startLine.trim.startsWith(firstMLQuote)
    val multipleLines = endLineNumber != startLineNumber

    val needNewLineBefore = settings.quotesOnNewLine && multipleLines && !startsOnNewLine

    // We don't need to add margin chars to the lines which have a injection with block expression which takes multiple lines
    val linesWithoutMargins: Set[Int] = literal match {
      case interpolated: ScInterpolatedStringLiteral =>
        val blockInjections = interpolated.getInjections.filterByType[ScBlockExpr]
        blockInjections.flatMap { block =>
          val range = block.getTextRange
          val lineStart = document.getLineNumber(range.getStartOffset)
          val lineEnd = document.getLineNumber(range.getEndOffset)

          //note: if block is not multiline, the lines set will be simply empty
          (lineStart + 1) to lineEnd
        }.toSet
      case _ =>
        Set.empty
    }

    val quotesIndent = if (needNewLineBefore) {
      val oldIndent = settings.calcIndentSize(text.subSequence(startLineOffset, literalStart))
      oldIndent + settings.regularIndent
    } else {
      settings.getSmartLength(text.subSequence(startLineOffset, literalStart))
    }
    val marginIndent = quotesIndent + interpolatorPrefixLength(literal) + settings.marginIndent
    val marginChar = _marginChar

    IntentionPreviewUtils.write { () =>
      def insertIndent(lineNumber: Int, indent: Int, marginChar: Option[Char]): Unit = {
        val lineStart = document.getLineStartOffset(lineNumber)
        val indentStr = settings.getSmartSpaces(indent) + marginChar.getOrElse("")
        document.insertString(lineStart, indentStr)
      }

      val addStripMarginCall = multipleLines && !hasStripMarginCall(literal)
      if (addStripMarginCall) {
        insertStripMargin(document, literal, marginChar)
      }

      for {
        lineNumber <- startLineNumber + 1 to endLineNumber
        if !linesWithoutMargins.contains(lineNumber)
      } {
        insertIndent(lineNumber, marginIndent, Some(marginChar))
      }

      if (needNewLineBefore) {
        document.insertString(literalStart, "\n")
        insertIndent(startLineNumber + 1, quotesIndent, None)
      }
    }

    val caretIsAfterNewLine = text.charAt((caretOffset - 1).max(0)) == '\n'
    val caretShift = if (caretIsAfterNewLine)
      1 + marginIndent // if caret is at new line then indent and margin char will be inserted AFTER caret
    else if (caretOffset == literalStart && needNewLineBefore)
      1 + quotesIndent // if caret is at literal start then indent and new line will be inserted AFTER caret
    else
      0 // otherwise caret will be automatically shifted by document.insertString, no need to fix it
    caretShift
  }

  /**
   * @return range of content inside string quotes e.g. for """abc""" returns (3, 6)
   * maybe move to psi?
   */
  def contentRange(string: ScLiteral): TextRange =
    string match {
      case interpolated: ScInterpolatedStringLiteral =>
        val refLength = interpolated.reference.map(_.getTextLength).getOrElse(0)
        val delta = quotesLength(string)
        val rangeWithoutRef = string.getTextRange.shiftStart(refLength)
        rangeWithoutRef.shrink(delta)
      case _: ScStringLiteral =>
        val delta = if(string.isMultiLineString) 3 else 1
        string.getTextRange.shrink(delta)
      case _ =>
        string.getTextRange
    }

  private def quotesLength(string: ScLiteral): Int =
    if (string.isMultiLineString) 3 else 1
}

class MultilineStringSettings(project: Project) {
  private val settings = CodeStyle.getSettings(project)
  private val scalaSettings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

  val useTabs: Boolean = settings.useTabCharacter(ScalaFileType.INSTANCE)
  val tabSize: Int = settings.getTabSize(ScalaFileType.INSTANCE)
  val regularIndent: Int = settings.getIndentOptions(ScalaFileType.INSTANCE).INDENT_SIZE
  val closingQuotesOnNewLine: Boolean = scalaSettings.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE
  val insertMargin: Boolean = scalaSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER
  val supportMultilineString: Boolean = scalaSettings.supportMultilineString()
  val marginIndent: Int = scalaSettings.MULTILINE_STRING_MARGIN_INDENT
  val quotesOnNewLine: Boolean = scalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE

  def getSpaces(count: Int): String = StringUtil.repeat(" ", count)

  def getSmartSpaces(count: Int): String = if (useTabs) {
    StringUtil.repeat("\t", count / tabSize) + StringUtil.repeat(" ", count % tabSize)
  } else {
    StringUtil.repeat(" ", count)
  }

  def getSmartLength(line: CharSequence): Int = {
    val tabsLength = if (useTabs) line.count(_ == '\t') * (tabSize - 1) else 0
    line.length + tabsLength
  }

  def calcIndentSize(line: CharSequence): Int = {
    // TODO: reuse IndentUtil.calcIndent
    if (useTabs) {
      val tabsCount = line.prefixLength(_ == '\t')
      tabsCount * tabSize + line.subSequence(tabsCount, line.length).prefixLength(_ == ' ')
    } else {
      line.prefixLength(_ == ' ')
    }
  }

  def getPrefix(line: String): String = getSmartSpaces(calcIndentSize(line))
}
