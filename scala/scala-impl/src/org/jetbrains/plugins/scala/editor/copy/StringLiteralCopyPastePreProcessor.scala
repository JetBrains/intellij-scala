package org.jetbrains.plugins.scala.editor.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.{LineTokenizer, StringUtil}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @see [[MultiLineStringCopyPastePreProcessor]]
 * @see java version [[com.intellij.codeInsight.editorActions.StringLiteralCopyPasteProcessor]]
 */
class StringLiteralCopyPastePreProcessor extends CopyPastePreProcessor {

  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    if (!file.is[ScalaFile])
      return text

    val literal = startOffsets.zip(endOffsets).forall { case (start, end) =>
      val e = file.findElementAt(start)
      e != null && e.elementType == ScalaTokenTypes.tSTRING && {
        val range = e.getTextRange
        range.getStartOffset < start && end < range.getEndOffset
      }
    }
    if (literal) StringUtil.unescapeStringCharacters(text) else null
  }

  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    if (!file.is[ScalaFile])
      return text

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)

    val selectionModel = editor.getSelectionModel
    val selectionStart = selectionModel.getSelectionStart
    val selectionEnd = selectionModel.getSelectionEnd

    val elementAtStart = file.findElementAt(selectionStart)
    val elementAtEnd = if (selectionStart == selectionEnd) elementAtStart else file.findElementAt(selectionEnd)
    if (elementAtStart == null || elementAtEnd == null)
      return text

    val elementTypeAtStart = elementAtStart.elementType
    val elementTypeAtEnd = elementAtEnd.elementType

    //Q: what is this condition for? Not covered in tests
    if ((elementTypeAtStart == ScalaTokenTypes.tSTRING || elementTypeAtStart == ScalaTokenTypes.tCHAR) && rawText != null && rawText.rawText != null)
      rawText.rawText
    else if (elementTypeAtStart == ScalaTokenTypes.tSTRING && elementTypeAtEnd == ScalaTokenTypes.tSTRING) {
      val tokens = LineTokenizer.tokenize(text.toCharArray, false, true)
      tokens.map(line => StringUtil.escapeStringCharacters(line)).mkString("\\n")
    }
    else text
  }
}