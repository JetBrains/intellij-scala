package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.format.{Text, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.MultilineStringUtil

final class StringToMultilineStringIntention extends PsiElementBaseIntentionAction {

  import StringToMultilineStringIntention._

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.regular.multi.line.string.conversion")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val maybeText = literalParent(element).collect {
      case lit if lit.isMultiLineString => ScalaCodeInsightBundle.message("convert.to.normal.string")
      case lit if lit.isString => ScalaCodeInsightBundle.message("convert.to.multiline.string")
    }

    maybeText.foreach(setText)
    maybeText.isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!element.isValid) return

    val lit = literalParent(element)
      .filter(_.isString)
      .getOrElse(return)

    if (!FileModificationService.getInstance.preparePsiElementForWrite(element)) return
    val containingFile = element.getContainingFile

    if (lit.isMultiLineString) multilineToRegular(lit, editor)
    else regularToMultiline(lit, editor)

    UndoUtil.markPsiFileForUndo(containingFile)
  }
}

object StringToMultilineStringIntention {
  private val Quote = "\""

  import MultilineStringUtil.{MultilineQuotes => Quotes, MultilineQuotesEscaped => QuotesEscaped}

  private def literalParent(element: PsiElement): Option[ScLiteral] =
    element.parentOfType(classOf[ScLiteral], strict = false)

  private def regularToMultiline(literal: ScLiteral, editor: Editor): Unit = {
    import literal.projectContext

    val document = editor.getDocument
    val documentText = document.getImmutableCharSequence

    val literalRange = literal.getTextRange
    val caretModel = editor.getCaretModel
    val caretOffset = caretModel.getOffset

    def fixCaretPosition(newOffset: Int): Unit = {
      if (literalRange.contains(caretOffset)) {
        inWriteAction {
          caretModel.moveToOffset(newOffset)
        }
      }
    }

    def addMargins(literalReplaced: PsiElement, interpolatorLength: Int, extraCaretOffset: Int = 0): Unit = {
      val caretShiftFromQuoteStart = caretOffset - literalRange.getStartOffset - interpolatorLength
      if (caretShiftFromQuoteStart > 0) {
        val textBeforeCaret = documentText.subSequence(literalRange.getStartOffset, caretOffset)
        val newLinesBeforeCaret = StringUtils.countMatches(textBeforeCaret, "\\n")
        // +2 extra quotes
        // each new line sequence ("\\n") is replaced with a single new line char ('\n') after converting to multiline
        fixCaretPosition(caretOffset + 2 - newLinesBeforeCaret + extraCaretOffset)
      }

      val caretShift = MultilineStringUtil.addMarginsAndFormatMLString(literalReplaced, document, caretModel.getOffset)
      if (caretShift > 0) {
        fixCaretPosition(caretModel.getOffset + caretShift)
      }
    }

    literal match {
      case interpolated: ScInterpolatedStringLiteral =>
        val prefix = interpolated.referenceName
        val parts = InterpolatedStringParser.parse(interpolated).getOrElse(Nil)
        val content = InterpolatedStringFormatter.formatContent(parts, toMultiline = true)
        val newLiteralText = s"$prefix$Quotes$content$Quotes"
        val newLiteral = createExpressionFromText(newLiteralText)
        val replaced = interpolated.replace(newLiteral)
        addMargins(replaced, prefix.length)

      case LiteralValue(s: String) =>
        val contentWithoutR = s.replace("\r", "")
        val (prefix, content) = if (s.contains(Quotes)) {
          // non-interpolated multiline string can not contain tripple quotes, see https://github.com/scala/bug/issues/6476
          ("s", contentWithoutR.replace("$", "$$").replace(Quotes, QuotesEscaped))
        } else {
          ("", contentWithoutR)
        }
        val newLiteralText = s"$prefix$Quotes$content$Quotes"
        val newLiteral = createExpressionFromText(newLiteralText)
        val replaced = literal.replace(newLiteral)
        addMargins(replaced, 0, prefix.length)

      case _ =>
    }
  }

  private def multilineToRegular(literal: ScLiteral, editor: Editor): Unit = {
    implicit val projectContext: ProjectContext = literal.projectContext

    val document = editor.getDocument
    val documentText = document.getImmutableCharSequence

    val literalRange = literal.getTextRange
    val caretModel = editor.getCaretModel
    val caretOffset = caretModel.getOffset

    def fixCaretPosition(interpolatorLength: Int): Unit = {
      val caretShiftFromQuotesStart = caretOffset - literalRange.getStartOffset - interpolatorLength
      if (literalRange.contains(caretOffset) && caretShiftFromQuotesStart > 0) {
        val textBeforeCaret = documentText.subSequence(literalRange.getStartOffset + 1, caretOffset).toString

        // each new line character ('\n') of multiline string is replaced with a new line sequence ("\\n")
        // after converting to regular string
        val shiftNewLines = textBeforeCaret.toString.count(_ == '\n')
        val shiftQuotes = -2.min(caretShiftFromQuotesStart)
        val shiftStrippedMargins: Int = literal match {
          case WithStrippedMargin(_, marginChar) =>
            val allMarginsLength = textBeforeCaret.length - textBeforeCaret.stripMargin(marginChar).length
            // fix case when caret is placed inside margin itself
            val spacesInsideMargin = spacesBeforeCaretInsideMargin(textBeforeCaret)
            -allMarginsLength - spacesInsideMargin
          case _ => 0
        }

        val caretShift = shiftNewLines + shiftQuotes + shiftStrippedMargins
        inWriteAction {
          caretModel.moveToOffset(caretOffset + caretShift)
        }
      }
    }

    literal match {
      case interpolated: ScInterpolatedStringLiteral =>
        val prefix = interpolated.referenceName
        val (toReplace, parts) = literal match {
          case WithStrippedMargin(expr, _) =>
            (expr, StripMarginParser.parse(literal).getOrElse(Nil))
          case _ =>
            (interpolated, InterpolatedStringParser.parse(interpolated).getOrElse(Nil))
        }
        val content = InterpolatedStringFormatter.formatContent(parts)
        val newLiteralText = s"$prefix$Quote$content$Quote"
        val newLiteral = createExpressionFromText(newLiteralText)
        toReplace.replace(newLiteral)
        fixCaretPosition(prefix.length)

      case _ =>
        val (toReplace, parts: Seq[StringPart]) = literal match {
          case WithStrippedMargin(expr, _) =>
            (expr, StripMarginParser.parse(literal).getOrElse(Nil))
          case LiteralValue(s: String) =>
            (literal, List(Text(s)))
          case _ =>
            (literal, Nil)
        }
        parts match {
          case Seq(Text(s)) =>
            val content = StringUtil.escapeStringCharacters(s)
            val newLiteralText = s"$Quote$content$Quote"
            val newLiteral = createExpressionFromText(newLiteralText)
            toReplace.replace(newLiteral)
            fixCaretPosition(0)
          case _ =>
        }
    }
  }

  /* Example 1:
   *  """one
   *    |two
   *    <caret>|three""".stripMargin -> 5 (4 spaces + 1 new line)
   *
   * Example 2:
   *   """one
   *     |two
   *     |th<caret>ree""".stripMargin -> 0 (caret not inside margin)
   */
  private def spacesBeforeCaretInsideMargin(textBeforeCaret: String): Int = {
    var idx = textBeforeCaret.length - 1
    var spaces = 0
    while (idx > 0) {
      textBeforeCaret.charAt(idx) match {
        case ' ' | '\t' => spaces += 1
        case '\n' => return spaces
        case _ => return 0
      }
      idx -= 1
    }
    0
  }

  private object LiteralValue {
    def unapply(arg: ScLiteral): Option[AnyRef] = Option(arg.getValue)
  }
}
