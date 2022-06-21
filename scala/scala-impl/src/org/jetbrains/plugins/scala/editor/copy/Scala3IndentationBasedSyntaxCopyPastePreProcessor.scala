package org.jetbrains.plugins.scala.editor.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Caret, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.Scala3IndentationBasedSyntaxUtils.{indentWhitespace, lineIndentWhitespace}
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils.findElementAtCaret_WithFixedEOF
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiFileExt}

class Scala3IndentationBasedSyntaxCopyPastePreProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    if (!file.useIndentationBasedSyntax)
      return null

    if (startOffsets.length != 1 || endOffsets.length != 1)
      return null

    // only change indentation for multi-line texts
    if (isSingleLine(text))
      return null

    // get fist non-whitespace element in selection
    var firstElement = file.findElementAt(startOffsets(0)).toOption
    if (firstElement.exists(el => el.isWhitespace || el.is[PsiComment]))
      firstElement = firstElement.get.nextVisibleLeaf(true)
    if (firstElement.isEmpty || endOffsets(0) <= firstElement.get.startOffset)
      return null

    // strip first-line indentation from all lines
    val leadingSpaceOnLine = indentWhitespace(firstElement.get)
    text
      .concat("\n") // linesIterator removes last \n
      .linesIterator
      .map(_.stripPrefix(leadingSpaceOnLine))
      .mkString("\n")
  }

  // the formatter is always run on pasted snippets, so we just need to adjust indentation so that the formatter recognizes it
  // this only called on single caret, paste for multiple carets is handled as raw text
  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    // only change indentation for multi-line texts
    if (isSingleLine(text))
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
      .linesIterator
      .map(indentWhitespace + _)
      .mkString("\n")
      .stripLeading() // first line does not need to be indented because caret is already indented
  }

  @inline private def isSingleLine(text: String): Boolean = {
    val lineBreaks = text.count(_ == '\n')
    // don't adapt indentation for copying line without selection
    lineBreaks == 0 || lineBreaks == 1 && text.endsWith("\n")
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
