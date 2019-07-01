package org.jetbrains.plugins.scala
package util

import java.util.regex.Pattern

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format.WithStrippedMargin
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScReference, literals}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

import scala.collection.mutable.ArrayBuffer

object MultilineStringUtil {
  val MultilineQuotes = "\"\"\""

  private val escaper = Pattern.compile("([^a-zA-z0-9])")

  def escapeForRegexp(s: String): String = {
    escaper.matcher(s).replaceAll("\\\\$1")
  }

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
        case _: ScReference => //if (ref.nameId.getText == methodName) return false
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

  def hasMarginChars(element: PsiElement, marginChar: String): Boolean = {
    element.getText.replace("\r", "").split(s"\n[ \t]*${escapeForRegexp(marginChar)}").length > 1
  }

  def needAddStripMargin(element: PsiElement, marginChar: String): Boolean = {
    findAllMethodCallsOnMLString(element, "stripMargin").isEmpty && !hasMarginChars(element, marginChar)
  }

  /** @return true <br>
   *          1) if multiline string literal has stripMargin on the call site and at least one line with margin<br>
   *          2) all non-empty lines start with a margin<br><br>
   *          false  otherwise
   */
  def looksLikeUsesMargins(literal: ScLiteral): Boolean = {
    val text = literal.contentText
    val lines = text.lines
      .map(_.trim)
      .filterNot(_.isEmpty)

    literal match {
      case WithStrippedMargin(_, marginChar) =>
        // TODO: test
        !lines.hasNext || lines.exists(_.startsWith(marginChar))
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

  def insertStripMargin(document: Document, literal: ScLiteral, marginChar: Char) {
    if (needAddStripMargin(literal, "" + marginChar)) {
      val stripText = if (marginChar == '|') ".stripMargin"
      else s".stripMargin('$marginChar')"
      document.insertString(literal.getTextRange.getEndOffset, stripText)
    }
  }

  def getMarginChar(element: PsiElement): Char = findAllMethodCallsOnMLString(element, "stripMargin") match {
    case Array(Array(literals.ScCharLiteral(value), _*), _*) => value
    case _ => CodeStyle.getSettings(element.getProject)
      .getCustomSettings(classOf[ScalaCodeStyleSettings])
      .getMarginChar
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
          if (inf.operation.getText == methodName) {
            if (prevParent != parent.getFirstChild) return callsArray
            calls += Array(inf.right)
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
    element.withParentsInFile.collect {
      case lit: ScLiteral if lit.isMultiLineString => lit
    }.toStream.headOption
  }

  def isMLString(element: PsiElement): Boolean = element match {
    case lit: ScLiteral if lit.isMultiLineString => true
    case _ => false
  }

  def interpolatorPrefixLength(literal: ScLiteral): Int = interpolatorPrefix(literal).length

  def interpolatorPrefix(literal: ScLiteral): String = literal match {
    case stringLiteral: ScInterpolatedStringLiteral => stringLiteral.referenceName
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

  /**
   * @param element     ScLiteral multiline element that needs margins
   * @param document    document containing element
   * @param caretOffset current caret offset inside document
   * @return additional caret offset needed to preserve original caret position relative to surrounding string parts
   */
  def addMarginsAndFormatMLString(element: PsiElement, document: Document, caretOffset: Int = 0): Int = {
    val settings = new MultilineStringSettings(element.getProject)
    if (!settings.insertMargin) return 0

    PsiDocumentManager.getInstance(element.getProject).doPostponedOperationsAndUnblockDocument(document)

    element match {
      case literal: ScLiteral if literal.isMultiLineString =>
        val firstMLQuote = interpolatorPrefix(literal) + MultilineQuotes

        val (literalStart, literalEnd) = (literal.getTextRange.getStartOffset, literal.getTextRange.getEndOffset)
        val (startLineNumber, endLineNumber) = (document.getLineNumber(literalStart), document.getLineNumber(literalEnd))
        val (startLineOffset, startLineEndOffset) = (document.getLineStartOffset(startLineNumber), document.getLineEndOffset(startLineNumber))

        val text = document.getImmutableCharSequence
        val startLine = text.substring(startLineOffset, startLineEndOffset)
        val startsOnNewLine = startLine.trim.startsWith(firstMLQuote)
        val multipleLines = endLineNumber != startLineNumber
        val needNewLineBefore = settings.quotesOnNewLine && multipleLines && !startsOnNewLine
        val marginChar = getMarginChar(literal)

        val quotesIndent = if (needNewLineBefore) {
          val oldIndent = settings.calcIndentSize(text.subSequence(startLineOffset, literalStart))
          oldIndent + settings.regularIndent
        } else {
          settings.getSmartLength(text.subSequence(startLineOffset, literalStart))
        }
        val marginIndent = quotesIndent + interpolatorPrefixLength(literal) + settings.marginIndent

        inWriteAction {
          def insertIndent(lineNumber: Int, indent: Int, marginChar: Option[Char]) {
            val lineStart = document.getLineStartOffset(lineNumber)
            val indentStr = settings.getSmartSpaces(indent) + marginChar.getOrElse("")
            document.insertString(lineStart, indentStr)
          }

          if (multipleLines)
            insertStripMargin(document, literal, marginChar)

          for (lineNumber <- startLineNumber + 1 to endLineNumber) {
            insertIndent(lineNumber, marginIndent, Some(marginChar))
          }

          if (needNewLineBefore) {
            document.insertString(literalStart, "\n")
            insertIndent(startLineNumber + 1, quotesIndent, None)
          }
        }

        val caretIsAfterNewLine = text.charAt((caretOffset - 1).max(0)) == '\n'
        val caretShift = if (caretIsAfterNewLine) {
          1 + marginIndent // if caret is at new line then indent and margin char will be inserted AFTER caret
        } else if (caretOffset == literalStart && needNewLineBefore) {
          1 + quotesIndent // if caret is at literal start then indent and new line will be inserted AFTER caret
        } else {
          0 // otherwise caret will be automatically shifted by document.insertString, no need to fix it
        }
        caretShift
      case something =>
        throw new IllegalStateException(s"Need multiline string literal, but get: ${something.getText}")
    }
  }
}

class MultilineStringSettings(project: Project) {
  private val settings = CodeStyle.getSettings(project)
  private val scalaSettings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

  val defaultMarginChar: Char = settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).getMarginChar
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
