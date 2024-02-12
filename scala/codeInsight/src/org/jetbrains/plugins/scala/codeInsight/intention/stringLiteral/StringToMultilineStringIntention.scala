package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.MultilineStringUtil

final class StringToMultilineStringIntention extends PsiElementBaseIntentionAction {

  import StringToMultilineStringIntention._

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.regular.multi.line.string.conversion")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val stringParent = stringLiteralParent(element)
    val maybeText = stringParent.collect {
      case lit if lit.isMultiLineString => ScalaCodeInsightBundle.message("convert.to.normal.string")
      case lit if lit.hasValidClosingQuotes => ScalaCodeInsightBundle.message("convert.to.multiline.string")
    }

    maybeText.foreach(setText)
    maybeText.isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!element.isValid)
      return

    val lit = stringLiteralParent(element)
      .getOrElse(return)

    if (!IntentionPreviewUtils.prepareElementForWrite(element))
      return
    val containingFile = element.getContainingFile

    if (lit.isMultiLineString) multilineToRegular(lit, editor)
    else regularToMultiline(lit, editor)

    UndoUtil.markPsiFileForUndo(containingFile)
  }
}

object StringToMultilineStringIntention {
  private val Quote = "\""

  import MultilineStringUtil.{MultilineQuotes => Quotes}

  private def stringLiteralParent(element: PsiElement): Option[ScStringLiteral] =
    element.parentOfType(classOf[ScStringLiteral], strict = false)

  private def regularToMultiline(literal: ScStringLiteral, editor: Editor): Unit = {
    import literal.projectContext

    val document = editor.getDocument
    val documentText = document.getImmutableCharSequence

    val literalRange = literal.getTextRange
    val caretModel = editor.getCaretModel
    val caretOffset = caretModel.getOffset

    // todo (minor) it doesn't restore caret pos for all tests from StringToMultilineStringIntentionTest, especially with escape sequences
    def fixCaretPosition(newOffset: Int): Unit = {
      if (literalRange.contains(caretOffset)) {
        IntentionPreviewUtils.write(() => caretModel.moveToOffset(newOffset))
      }
    }

    def addMargins(
      literalReplaced: ScStringLiteral,
      interpolatorLength: Int,
    ): Unit = {
      if (!literalReplaced.isMultiLineString)
        return

      val caretShiftFromQuoteStart = caretOffset - literalRange.getStartOffset - interpolatorLength
      if (caretShiftFromQuoteStart > 0) {
        val textBeforeCaret = documentText.subSequence(literalRange.getStartOffset, caretOffset)
        val newLinesBeforeCaret = StringUtils.countMatches(textBeforeCaret, "\\n")
        // +2 extra quotes
        // each new line sequence ("\\n") is replaced with a single new line char ('\n') after converting to multiline
        fixCaretPosition(caretOffset + 2 - newLinesBeforeCaret)
      }

      val caretShift = MultilineStringUtil.addMarginsAndFormatMLString(literalReplaced, document, caretModel.getOffset)
      if (caretShift > 0) {
        val newCaretOffset = caretModel.getOffset + caretShift
        fixCaretPosition(newCaretOffset)
      }
    }

    val parts = ScStringLiteralParser.parse(literal, checkStripMargin = true).getOrElse(Nil)
    val prefix = interpolatorPrefix(literal)
    val content = InterpolatedStringFormatter.formatContent(parts, prefix, toMultiline = true)
    val newLiteralText = s"$prefix$Quotes$content$Quotes"
    val newLiteral = createExpressionFromText(newLiteralText, literal)
    val replaced = literal.replace(newLiteral).asInstanceOf[ScStringLiteral]
    addMargins(replaced, interpolatorLength = prefix.length)
  }

  private def multilineToRegular(literal: ScStringLiteral, editor: Editor): Unit = {
    implicit val projectContext: ProjectContext = literal.projectContext

    val document = editor.getDocument
    val documentText = document.getImmutableCharSequence

    val literalRange = literal.getTextRange
    val caretModel = editor.getCaretModel
    val caretOffset = caretModel.getOffset

    // todo (minor) it doesn't restore caret pos for all tests from StringToMultilineStringIntentionTest, especially with escape sequences
    def fixCaretPosition(interpolatorLength: Int): Unit = {
      val caretShiftFromQuotesStart = caretOffset - literalRange.getStartOffset - interpolatorLength
      if (literalRange.contains(caretOffset) && caretShiftFromQuotesStart > 0) {
        val textBeforeCaret = documentText.subSequence(literalRange.getStartOffset + 1, caretOffset).toString

        // each new line character ('\n') of multiline string is replaced with a new line sequence ("\\n")
        // after converting to regular string
        val shiftNewLines = textBeforeCaret.count(_ == '\n')
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
        IntentionPreviewUtils.write { () =>
          caretModel.moveToOffset(caretOffset + caretShift)
        }
      }
    }

    val (elementToReplace, parts: Seq[StringPart]) = literal match {
      case WithStrippedMargin(literalWithMarginCall, _) =>
        (literalWithMarginCall, StripMarginParser.parse(literal).getOrElse(Nil))
      case _                                            =>
        (literal, ScStringLiteralParser.parse(literal, checkStripMargin = false).getOrElse(Nil))
    }
    // If we convert multiline `raw` interpolated string with new lines, we need to somehow encode the new lines
    // `raw` string do not support usual escape sequences, so there are 3 options:
    // 1. convert to raw"" and use unicode escape sequence `\u000A` (`\n`)
    // 2. convert to raw"" and use injection of new line ${'\n'}
    // 3. convert to s"" and escape all backslashes
    // Here we stick to 3. approach
    val prefix = interpolatorPrefix(literal) match {
      case "raw" if parts.exists(hasNewLine) => "s"
      case p => p
    }
    val content = InterpolatedStringFormatter.formatContent(parts, prefix, toMultiline = false)
    val newLiteralText = s"$prefix$Quote$content$Quote"
    val newLiteral = createExpressionFromText(newLiteralText, literal)
    elementToReplace.replace(newLiteral)
    fixCaretPosition(prefix.length)
  }

  private def hasNewLine(part: StringPart): Boolean =
    part match {
      case Text(t) => t.contains('\n')
      case _ => false
    }

  private def interpolatorPrefix(literal: ScStringLiteral): String =
    literal match {
      case literal: ScInterpolatedStringLiteral =>  literal.referenceName
      case _ => ""
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
}
