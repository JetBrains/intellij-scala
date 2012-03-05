package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import lang.psi.api.ScalaFile
import lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.extensions
import com.intellij.psi.{PsiElement, PsiDocumentManager, PsiFile}
import com.intellij.psi.xml.XmlTokenType
import lang.psi.api.expr.xml.ScXmlStartTag
import lang.scaladoc.lexer.ScalaDocTokenType
import lang.lexer.ScalaTokenTypes

/**
 * User: Dmitry Naydanov
 * Date: 2/24/12
 */

class ScalaBackspaceHandler extends BackspaceHandlerDelegate {
  def beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
    if (!file.isInstanceOf[ScalaFile]) return

    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset - 1)
    if (element == null) return

    if (needCorrecrWiki(element)) {
      extensions.inWriteAction {
        val document = editor.getDocument

        if (element.getParent.getLastChild != element) {
          val tagToDelete = element.getParent.getLastChild
          val textLength =
            if (tagToDelete.getNode.getElementType != ScalaDocTokenType.DOC_BOLD_TAG) tagToDelete.getTextLength else 1
          document.deleteString(tagToDelete.getTextOffset, tagToDelete.getTextOffset + textLength)
        } else {
          document.deleteString(element.getTextOffset, element.getTextOffset + 2)
          editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
        }

        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
      }
    } else if (element.getNode.getElementType == XmlTokenType.XML_NAME && element.getParent != null && element.getParent.isInstanceOf[ScXmlStartTag]) {
      val openingTag = element.getParent.asInstanceOf[ScXmlStartTag]
      val closingTag = openingTag.getClosingTag

      if (closingTag != null && closingTag.getText.substring(2, closingTag.getTextLength - 1) == openingTag.getTagName) {
        extensions.inWriteAction {
          val offsetInName = editor.getCaretModel.getOffset - element.getTextOffset + 1
          editor.getDocument.deleteString(closingTag.getTextOffset + offsetInName, closingTag.getTextOffset + offsetInName + 1)
          PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
        }
      }
    } else if (element.getNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING && offset - element.getTextOffset == 3) {
      val closingQuotesOffset = element.getTextOffset + element.getTextLength - 3
      extensions.inWriteAction {
        editor.getDocument.deleteString(closingQuotesOffset, closingQuotesOffset + 3)
        editor.getCaretModel.moveCaretRelatively(-1, 0, false, false, false)
        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
      }
    }

    def needCorrecrWiki(element: PsiElement) = (element.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType]
            || element.getText == "{{{") && (element.getParent.getLastChild != element ||
            element.getText == "'''" && element.getPrevSibling != null && element.getPrevSibling.getText == "'")
  }

  def charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean = false
}
