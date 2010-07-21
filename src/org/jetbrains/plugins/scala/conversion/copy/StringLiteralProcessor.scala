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
    var s = text
    
    val document = editor.getDocument
    PsiDocumentManager.getInstance(project).commitDocument(document)

    var caretOffset = editor.getCaretModel.getOffset
    var elementAtCaret = file.findElementAt(caretOffset)

    if (elementAtCaret.isInstanceOf[PsiElement] && caretOffset > elementAtCaret.getTextOffset) {
      val tokenType = elementAtCaret.getNode.getElementType
      if (tokenType == ScalaTokenTypes.tSTRING) {
        if (rawText != null && rawText.rawText != null) 
          return rawText.rawText
        
        var builder = new StringBuilder(s.length)
        var settings = CodeStyleSettingsManager.getSettings(project)
        var breaker = if (settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE) "\\n\"\n+ \"" else "\\n\" +\n\""
        val lines = LineTokenizer.tokenize(s.toCharArray, false, true)

        var i = 0
        while (i < lines.length) {
          var line = lines(i)
          builder.append(StringUtil.escapeStringCharacters(line))
          if (i != lines.length - 1) 
            builder.append(breaker)
          i += 1
        }
        
        s = builder.toString
      } else if (tokenType == ScalaTokenTypes.tCHAR) {
        if (rawText != null && rawText.rawText != null) 
          return rawText.rawText
      }
    }

    s
  }
}