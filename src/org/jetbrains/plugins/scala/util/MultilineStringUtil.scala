package org.jetbrains.plugins.scala
package util

import java.util.regex.Pattern

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.StdType

import scala.collection.mutable.ArrayBuffer


/**
 * Nikolay.Tropin
 * 2014-03-13
 */

object MultilineStringUtil {
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

  def hasMarginChars(element: PsiElement, marginChar: String) = {
    val escaper = Pattern.compile("([^a-zA-z0-9])")
    val escapedMarginChar = escaper.matcher(marginChar).replaceAll("\\\\$1")
    element.getText.replace("\r", "").split(s"\n[ \t]*$escapedMarginChar").length > 1
  }

  def needAddStripMargin(element: PsiElement, marginChar: String): Boolean = {
    findAllMethodCallsOnMLString(element, "stripMargin").isEmpty && !hasMarginChars(element, marginChar)
  }

  def needAddByType(literal: ScLiteral): Boolean = literal match {
    case interpolated: ScInterpolatedStringLiteral => interpolated.reference match {
      case Some(ref: ScReferenceExpression) =>
        ref.resolve() match {
          case funDef: ScFunction =>
            val tpe = funDef.returnType
            tpe.exists(scType => scType.canonicalText.endsWith("java.lang.String") || scType.canonicalText.endsWith("scala.Predef.String"))
          case _ => true
        }
      case _ => true
    }
    case _ => true
  }

  def insertStripMargin(document: Document, literal: ScLiteral, marginChar: Char) {
    if (needAddStripMargin(literal, "" + marginChar)) {
      document.insertString(literal.getTextRange.getEndOffset,
        if (marginChar == '|') ".stripMargin" else ".stripMargin(\'" + marginChar + "\')")
    }
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

  def addMarginsAndFormatMLString(element: PsiElement, document: Document) {
    val settings = new MultilineStringSettings(element.getProject)
    if (settings.supportLevel != ScalaCodeStyleSettings.MULTILINE_STRING_ALL) return

    def insertIndent(lineNumber: Int, indent: Int, marginChar: Option[Char]) {
      val lineStart = document.getLineStartOffset(lineNumber)
      document.insertString(lineStart, settings.getSmartSpaces(indent) + marginChar.getOrElse(""))
    }

    PsiDocumentManager.getInstance(element.getProject).doPostponedOperationsAndUnblockDocument(document)

    element match {
      case literal: ScLiteral if literal.isMultiLineString =>
        val firstMLQuote = interpolatorPrefix(literal) + multilineQuotes
        val literalOffsets = Seq(literal.getTextRange.getStartOffset, literal.getTextRange.getEndOffset)
        val Seq(startLineNumber, endLineNumber) = literalOffsets.map(document.getLineNumber)
        val literalStart = literalOffsets(0)
        val (startLineOffset, startLineEndOffset) = (document.getLineStartOffset(startLineNumber), document.getLineEndOffset(startLineNumber))

        val startsOnNewLine = document.getText.substring(startLineOffset, startLineEndOffset).trim.startsWith(firstMLQuote)
        val multipleLines = endLineNumber != startLineNumber
        val needNewLineBefore = settings.quotesOnNewLine && multipleLines && !startsOnNewLine
        val marginChar = getMarginChar(literal)

        extensions.inWriteAction {
          if (multipleLines) insertStripMargin(document, literal, marginChar)
          if (!needNewLineBefore) {
            val quotesIndent = settings.getSmartLength(document.getText.substring(startLineOffset, literalStart))
            val marginIndent = quotesIndent + interpolatorPrefixLength(literal) + settings.marginIndent
            for (lineNumber <- startLineNumber + 1 to endLineNumber) {
              insertIndent(lineNumber, marginIndent, Some(marginChar))
            }
          } else {
            val oldIndent = settings.prefixLength(document.getText.substring(startLineOffset, literalStart))
            val quotesIndent = oldIndent + settings.regularIndent
            val marginIndent = quotesIndent + interpolatorPrefixLength(literal) + settings.marginIndent
            for (lineNumber <- startLineNumber + 1 to endLineNumber) {
              insertIndent(lineNumber, marginIndent, Some(marginChar))
            }
            document.insertString(literalStart, "\n")
            insertIndent(startLineNumber + 1, quotesIndent, None)
          }
        }
      case something => throw new IllegalStateException(s"Need multiline string literal, but get: ${something.getText}")
    }
  }
}

class MultilineStringSettings(project: Project) {
  private val settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings
  private val scalaSettings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

  val defaultMarginChar = settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).MARGIN_CHAR
  val useTabs = settings.useTabCharacter(ScalaFileType.SCALA_FILE_TYPE)
  val tabSize = settings.getTabSize(ScalaFileType.SCALA_FILE_TYPE)
  val regularIndent = settings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).INDENT_SIZE
  val marginIndent = scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT
  val supportLevel = scalaSettings.MULTILINE_STRING_SUPORT
  val quotesOnNewLine = scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE

  def selectBySettings[T](ifIndent: => T)(ifAll: => T): T = {
    scalaSettings.MULTILINE_STRING_SUPORT match {
      case ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT => ifIndent
      case ScalaCodeStyleSettings.MULTILINE_STRING_ALL => ifAll
    }
  }

  def getSmartSpaces(count: Int) = if (useTabs) {
    StringUtil.repeat("\t", count/tabSize) + StringUtil.repeat(" ", count%tabSize)
  } else {
    StringUtil.repeat(" ", count)
  }

  def getSmartLength(line: String) = if (useTabs) line.length + line.count(_ == '\t')*(tabSize - 1) else line.length

  def prefixLength(line: String) = if (useTabs) {
    val tabsCount = line prefixLength (_ == '\t')
    tabsCount*tabSize + line.substring(tabsCount).prefixLength(_ == ' ')
  } else {
    line prefixLength (_ == ' ')
  }

  def getPrefix(line: String) = getSmartSpaces(prefixLength(line))
}
