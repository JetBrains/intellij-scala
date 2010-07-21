package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.project.Project
import java.lang.String
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.openapi.editor.{RawText, Editor}
import com.intellij.openapi.util.text.{LineTokenizer, StringUtil}
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}

/**
 * Pavel.Fatin, 21.07.2010
 */

class StringLiteralProcessor extends CopyPastePreProcessor {
  def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String) = {
    val literal = startOffsets.zip(endOffsets).forall {
      case (a, b) =>
        val e = file.findElementAt(a);
        e.isInstanceOf[PsiElement] && e.getNode.getElementType == ScalaTokenTypes.tSTRING &&
                a > e.getTextRange.getStartOffset && b < e.getTextRange.getEndOffset
    }
    if (literal) StringUtil.unescapeStringCharacters(text) else null
  }

  def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)

    var offset = editor.getCaretModel.getOffset
    var element = file.findElementAt(offset)

    if (element.isInstanceOf[PsiElement] && offset > element.getTextOffset) {
      val elementType = element.getNode.getElementType
      if ((elementType == ScalaTokenTypes.tSTRING || elementType == ScalaTokenTypes.tCHAR) 
              && rawText != null && rawText.rawText != null) {
          rawText.rawText
      } else if (elementType == ScalaTokenTypes.tSTRING) {
        LineTokenizer.tokenize(text.toCharArray, false, true).mkString("\\n")
      } else {
        text
      }
    } else {
      text
    }
  }
}