package org.jetbrains.plugins.scala.editor.copy

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiFile}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.Scala3IndentationBasedSyntaxUtils.indentWhitespace
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils.findElementAtCaret_WithFixedEOF
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil

class Scala3IndentationBasedSyntaxCopyPastePreProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    if (!file.is[ScalaFile] || !ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return null

    if (startOffsets.length != 1 || endOffsets.length != 1)
      return null

    // only change indentation for multi-line texts
    if (!text.contains('\n'))
      return null

    // get first non-whitespace element in selection
    var firstElement = file.findElementAt(startOffsets(0)).toOption
    if (firstElement.exists(el => el.isWhitespace || el.is[PsiComment]))
      firstElement = firstElement.get.nextVisibleLeaf(true)

    if (firstElement.isEmpty || endOffsets(0) <= firstElement.get.startOffset)
      // selection contains only whitespace or comments
      return null

    // add complete first-line indent to text
    val leadingSpaceOnLine = indentWhitespace(firstElement.get)
    leadingSpaceOnLine + text.dropWhile(c => c == ' ' || c == '\t')
  }

  // the formatter is always run on pasted snippets, so we just need to adjust indentation so that the formatter recognizes it
  // this only called on single caret, paste for multiple carets is handled as raw text
  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    if (!file.is[ScalaFile] || !ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return text

    // only change indentation for multi-line texts
    val lineBreaks = text.count(_ == '\n')
    if (lineBreaks == 0 || lineBreaks == 1 && text.endsWith("\n"))
      return text

    val settings = CodeStyle.getSettings(project)
    val tabSize = settings.getTabSize(ScalaFileType.INSTANCE)
    val useTabCharacter = settings.useTabCharacter(ScalaFileType.INSTANCE)

    val caret = editor.getCaretModel.getCurrentCaret
    val elementAtCaret = findElementAtCaret_WithFixedEOF(file, editor.getDocument, caret.getSelectionStart)
    val caretIndentWhitespace = indentWhitespace(elementAtCaret, caret.getSelectionStart, ignoreComments = true, ignoreElementsOnLine = true)
    val caretIndentSize = IndentUtil.calcIndent(caretIndentWhitespace, tabSize)

    val firstLineIndentWhitespace = text.takeWhile(c => c == ' ' || c == '\t')
    val firstLineIndentSize = IndentUtil.calcIndent(firstLineIndentWhitespace, tabSize)

    def fixIndent(line: String): String = {
      val lineIndentWhitespace = line.takeWhile(c => c == ' ' || c == '\t')
      val lineIndentSize = IndentUtil.calcIndent(lineIndentWhitespace, tabSize)
      val newIndentSize = lineIndentSize - firstLineIndentSize + caretIndentSize
      if (useTabCharacter)
        "\t" * (newIndentSize / tabSize) + line.stripPrefix(lineIndentWhitespace)
      else
        " " * newIndentSize + line.stripPrefix(lineIndentWhitespace)
    }

    // align all lines with caret indentation
    text
      .linesWithSeparators
      .map(fixIndent)
      .mkString("")
      // don't indent first line, as caret is already indented
      .stripPrefix(caretIndentWhitespace)
  }

  override def requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false
}
