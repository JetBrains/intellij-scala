package org.jetbrains.plugins.scala.editor.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.{LineTokenizer, StringUtil}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

class StringLiteralPreProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    val literal = startOffsets.zip(endOffsets).forall { case (a, b) =>
      val e = file.findElementAt(a)
      e.is[PsiElement] && e.getLanguage.isKindOf(ScalaLanguage.INSTANCE) && e.getNode != null &&
                e.getNode.getElementType == ScalaTokenTypes.tSTRING &&
                a > e.getTextRange.getStartOffset && b < e.getTextRange.getEndOffset
    }
    if (literal) StringUtil.unescapeStringCharacters(text) else null
  }

  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)

    val offset = editor.getSelectionModel.getSelectionStart
    val e = file.findElementAt(offset)

    if (e.is[PsiElement] && e.getLanguage.isKindOf(ScalaLanguage.INSTANCE) && offset > e.getTextOffset) {
      val elementType = if(e.getNode == null) null else e.getNode.getElementType
      if ((elementType == ScalaTokenTypes.tSTRING || elementType == ScalaTokenTypes.tCHAR) 
              && rawText != null && rawText.rawText != null) {
          rawText.rawText
      } else if (elementType == ScalaTokenTypes.tSTRING) {
        LineTokenizer.tokenize(text.toCharArray, false, true).map(line => StringUtil.escapeStringCharacters(line)).mkString("\\n")
      } else {
        text
      }
    } else {
      text
    }
  }
}