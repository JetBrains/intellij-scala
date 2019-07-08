package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Document, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.startsWithChar
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.conversion.copy.MultiLineStringCopyPasteProcessor._
import org.jetbrains.plugins.scala.extensions.{childOf, inWriteAction}
import org.jetbrains.plugins.scala.format.WithStrippedMargin
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.util.MultilineStringUtil

class MultiLineStringCopyPasteProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile,
                                startOffsets: Array[Int],
                                endOffsets: Array[Int],
                                text: String): String = (file, startOffsets, endOffsets) match {
    case (requiresMarginProcess(), Array(startOffset), Array(endOffset)) =>
      findMultilineStringParent(file.findElementAt(startOffset)) match {
        case Some(literal) if literal.getTextRange.containsRange(startOffset, endOffset) =>
          val marginChar = getMarginChar(literal)
          text.stripMargin(marginChar)
        case _ => null
      }
    case _ => null
  }

  override def preprocessOnPaste(project: Project,
                                 file: PsiFile,
                                 editor: Editor,
                                 text: String,
                                 rawText: RawText): String = {
    val result: Option[String] = for {
      f <- Option(file)
      if requiresMarginProcess(f)
      if !editor.getSelectionModel.hasSelection
      offset = editor.getCaretModel.getOffset
      element = file.findElementAt(offset)
      literal <- findMultilineStringParent(element)
    } yield {
      val isOneLine = !literal.textContains('\n')
      if (isOneLine && settings(file).MULTILINE_STRING_INSERT_MARGIN_ON_ENTER) {
        val marginChar = getMarginChar(element)
        literal match {
          case WithStrippedMargin(_, _) =>
          case _ =>
            inWriteAction {
              val stripMarginParams = if (marginChar == '|') "" else "('$marginChar')"
              editor.getDocument.insertString(literal.getTextRange.getEndOffset, s".stripMargin$stripMarginParams")
            }
        }
        text.replace("\n", "\n " + marginChar)
      } else if (MultilineStringUtil.looksLikeUsesMargins(literal)) {
        val marginChar = getMarginChar(element)
        val marginIsMissing = !startsWithChar(text.trim, marginChar)
        val startsFromNewLine = StringUtil.isEmptyOrSpaces(linePrefix(editor.getDocument, offset))
        val needMargin = marginIsMissing && startsFromNewLine
        val marginPrefix = if (needMargin) marginChar else ""
        marginPrefix + text.replace("\n", "\n " + marginChar)
      } else {
        text
      }
    }
    result
      .map(_.replace("\"\"\"", """\"\"\""""))
      .getOrElse(text)
  }
}

object MultiLineStringCopyPasteProcessor {
  private object requiresMarginProcess {
    def apply(file: PsiFile): Boolean = settings(file).MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE
    def unapply(file: ScalaFile): Boolean = apply(file)
  }

  private def settings(file: PsiFile): ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(file.getProject)

  private def findMultilineStringParent(element: PsiElement): Option[ScLiteral] = (element match {
    case literal: ScInterpolatedStringLiteral => Some(literal)
    case literal: ScLiteral => Some(literal)
    case _ childOf (literal: ScInterpolatedStringLiteral) => Some(literal)
    case _ childOf (literal: ScLiteral) => Some(literal)
    case _ => None
  }).filter(_.isMultiLineString)

  private def linePrefix(document: Document, offset: Int): String = {
    val lineStartOffset = document.getLineStartOffset(document.getLineNumber(offset))
    val range = new TextRange(lineStartOffset, offset)
    document.getText(range)
  }

  private def getMarginChar(element: PsiElement): Char = MultilineStringUtil.getMarginChar(element)
}
