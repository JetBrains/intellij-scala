package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlStartTag
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType

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

          if (ScaladocSyntaxElementType.canClose(element.getNode.getElementType, tagToDelete.getNode.getElementType)) {
            val textLength =
              if (tagToDelete.getNode.getElementType != ScalaDocTokenType.DOC_BOLD_TAG) tagToDelete.getTextLength else 1
            document.deleteString(tagToDelete.getTextOffset, tagToDelete.getTextOffset + textLength)
          }
        } else {
          document.deleteString(element.getTextOffset, element.getTextOffset + 2)
          editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
        }

        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
      }
    } else if (element.getNode.getElementType == ScalaXmlTokenTypes.XML_NAME && element.getParent != null && element.getParent.isInstanceOf[ScXmlStartTag]) {
      val openingTag = element.getParent.asInstanceOf[ScXmlStartTag]
      val closingTag = openingTag.getClosingTag

      if (closingTag != null && closingTag.getTextLength > 3 && closingTag.getText.substring(2, closingTag.getTextLength - 1) == openingTag.getTagName) {
        extensions.inWriteAction {
          val offsetInName = editor.getCaretModel.getOffset - element.getTextOffset + 1
          editor.getDocument.deleteString(closingTag.getTextOffset + offsetInName, closingTag.getTextOffset + offsetInName + 1)
          PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
        }
      }
    } else if (element.getNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING && offset - element.getTextOffset == 3) {
      correctMultilineString(element.getTextOffset + element.getTextLength - 3)
    } else if (element.getNode.getElementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER && element.getNextSibling != null &&
      element.getNextSibling.getNode.getElementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        extensions.inWriteAction {
          editor.getDocument.deleteString(element.getTextOffset + 1, element.getTextOffset + 2)
          PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
        }
    } else if (offset - element.getTextOffset == 3 &&
            element.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING &&
            element.getParent.getLastChild.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING_END &&
            element.getPrevSibling != null &&
            isMultilineInterpolatedStringPrefix(element.getPrevSibling.getNode.getElementType)) {
      correctMultilineString(element.getParent.getLastChild.getTextOffset)
    }

    @inline def isMultilineInterpolatedStringPrefix(tpe: IElementType) =
      Set(ScalaElementTypes.INTERPOLATED_PREFIX_LITERAL_REFERENCE,
        ScalaElementTypes.INTERPOLATED_PREFIX_PATTERN_REFERENCE, ScalaTokenTypes.tINTERPOLATED_STRING_ID) contains tpe

    def correctMultilineString(closingQuotesOffset: Int) {
      extensions.inWriteAction {
        editor.getDocument.deleteString(closingQuotesOffset, closingQuotesOffset + 3)
//        editor.getCaretModel.moveCaretRelatively(-1, 0, false, false, false) //http://youtrack.jetbrains.com/issue/SCL-6490
        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
      }
    }

    def needCorrecrWiki(element: PsiElement) = (element.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType]
            || element.getText == "{{{") && (element.getParent.getLastChild != element ||
            element.getText == "'''" && element.getPrevSibling != null && element.getPrevSibling.getText == "'")
  }

  def charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean = false
}
