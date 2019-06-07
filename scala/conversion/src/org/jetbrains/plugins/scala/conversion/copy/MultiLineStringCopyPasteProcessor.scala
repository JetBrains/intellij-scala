package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Document, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}

/**
 * User: Dmitry Naydanov
 * Date: 5/5/12
 */
final class MultiLineStringCopyPasteProcessor extends CopyPastePreProcessor {

  import MultiLineStringCopyPasteProcessor._
  import util.MultilineStringUtil.getMarginChar

  override def preprocessOnCopy(file: PsiFile,
                                startOffsets: Array[Int],
                                endOffsets: Array[Int],
                                text: String): String = (file, startOffsets, endOffsets) match {
    case (requiresMarginProcess(), Array(startOffset), Array(endOffset)) =>
      val maybeMarginChar = for {
        element <- findStringParent(file.findElementAt(startOffset))

        range = element.getTextRange
        if range.getStartOffset <= startOffset
        if range.getEndOffset >= endOffset
      } yield getMarginChar(element)

      maybeMarginChar.fold(null: String)(text.stripMargin)
    case _ => null
  }

  override def preprocessOnPaste(project: Project,
                                 file: PsiFile,
                                 editor: Editor,
                                 text: String,
                                 rawText: RawText): String = file match {
    case requiresMarginProcess() =>
      val offset = editor.getCaretModel.getOffset
      val element = file.findElementAt(offset)

      if (findStringParent(element).isEmpty ||
        offset < element.getTextOffset + 3) return text

      val marginChar = getMarginChar(element)

      import StringUtil._
      val newText = convertLineSeparators(text, "\n " + marginChar)
      if (!startsWithChar(text.trim, marginChar) &&
        isEmptyOrSpaces(lineAt(editor.getDocument, offset)))
        marginChar + newText
      else
        newText
    case _ => text
  }
}

object MultiLineStringCopyPasteProcessor {

  private object requiresMarginProcess {

    def unapply(file: ScalaFile): Boolean =
      ScalaCodeStyleSettings.getInstance(file.getProject)
        .MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE
  }

  private def findStringParent(element: PsiElement): Option[ScLiteral] = element match {
    case literal: ScInterpolatedStringLiteral => Some(literal)
    case literal: ScLiteral if literal.isMultiLineString => Some(literal)
    case _ childOf (literal: ScInterpolatedStringLiteral) => Some(literal)
    case _ => None
  }

  private def lineAt(document: Document, offset: Int) = {
    val range = new TextRange(
      document.getLineStartOffset(document.getLineNumber(offset)),
      offset
    )
    document.getText(range)
  }
}
