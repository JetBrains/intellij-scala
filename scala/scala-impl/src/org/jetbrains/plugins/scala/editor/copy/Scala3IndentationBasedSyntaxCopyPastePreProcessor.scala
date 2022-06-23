package org.jetbrains.plugins.scala.editor.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Caret, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.Scala3IndentationBasedSyntaxUtils.{indentWhitespace, lineIndentWhitespace}
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils.findElementAtCaret_WithFixedEOF
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class Scala3IndentationBasedSyntaxCopyPastePreProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    if (!file.is[ScalaFile] || !ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return null

    if (startOffsets.length != 1 || endOffsets.length != 1)
      return null

    // only change indentation for multi-line texts
    val lineBreaks = text.count(_ == '\n')
    if (lineBreaks == 0)
      return null

    // get fist non-whitespace element in selection
    var firstElement = file.findElementAt(startOffsets(0)).toOption
    if (firstElement.exists(el => el.isWhitespace || el.is[PsiComment]))
      firstElement = firstElement.get.nextVisibleLeaf(true)
    if (firstElement.isEmpty || endOffsets(0) <= firstElement.get.startOffset)
      return null

    // get leading whitespace
    val leadingSpaceOnLine = indentWhitespace(firstElement.get)

    // don't adapt indentation for copying single line without selection
    if (lineBreaks == 1 && text.endsWith("\n") && text.startsWith(leadingSpaceOnLine))
      return null

    // strip first-line indentation from all lines
    text
      .linesWithSeparators
      .map(_.stripPrefix(leadingSpaceOnLine))
      .mkString("")
  }

  // the formatter is always run on pasted snippets, so we just need to adjust indentation so that the formatter recognizes it
  // this only called on single caret, paste for multiple carets is handled as raw text
  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    if (!file.is[ScalaFile] || !ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return text

    // only change indentation for multi-line texts
    val lineBreaks = text.count(_ == '\n')
    if (lineBreaks == 0)
      return text

    // don't adapt indentation for copying line without selection
    if (lineBreaks == 1 && text.endsWith("\n"))
      return text

    // get indentation at caret
    val caret = editor.getCaretModel.getCurrentCaret
    val elementAtCaret = findElementAtCaret_WithFixedEOF(file, editor.getDocument, caret.getSelectionStart)
    val indentWhitespace = elementAtCaret match {
      case null => ""
      case ws: PsiWhiteSpace => lineWhitespaceToCaret(ws, caret)
      case el => lineIndentWhitespace(el)
    }

    // add caret indentation to all lines
    text
      .linesWithSeparators
      .map(indentWhitespace.concat)
      .mkString("")
      .stripPrefix(indentWhitespace)
  }

  private def lineWhitespaceToCaret(ws: PsiWhiteSpace, caret: Caret): String = {
    val wsTextToCaret = ws.getText.substring(0, caret.getSelectionStart - ws.startOffset)
    if (wsTextToCaret.contains('\n'))
      // caret starts from new line
      wsTextToCaret.substring(wsTextToCaret.lastIndexOf('\n') + 1)
    else
      // caret does not start from new line
      lineIndentWhitespace(ws)
  }

  override def requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false
}
