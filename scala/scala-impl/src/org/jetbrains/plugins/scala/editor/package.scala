package org.jetbrains.plugins.scala

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.{FileDocumentManagerImpl, LoadTextUtil}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType
import org.jetbrains.plugins.scala.extensions.invokeAndWait

/**
  * Nikolay.Tropin
  * 27-Oct-17
  */
package object editor {
  implicit class DocumentExt(private val document: Document) extends AnyVal {
    def commit(project: Project): Unit =
      PsiDocumentManager.getInstance(project).commitDocument(document)

    def lineStartOffset(offset: Int): Int =
      document.getLineStartOffset(document.getLineNumber(offset))

    def lineEndOffset(offset: Int): Int =
      document.getLineEndOffset(document.getLineNumber(offset))

    def virtualFile: Option[VirtualFile] =
      Option(FileDocumentManager.getInstance().getFile(document))

    def syncToDisk(project: Project): Unit =
      virtualFile.filter(_.isValid).foreach { file =>
        val requestor = FileDocumentManager.getInstance
        val separator = if (document.getLineCount > 1)
          FileDocumentManagerImpl.getLineSeparator(document, file)
        else
          "\n"
        val documentText = document.getText
        val text = if (separator != "\n")
          StringUtil.convertLineSeparators(documentText, separator)
        else
          documentText
        val modificationStamp = document.getModificationStamp
        invokeAndWait {
          inWriteAction {
            LoadTextUtil.write(project, file, requestor, text, modificationStamp)
          }
        }
      }
  }

  implicit class EditorExt(private val editor: Editor) extends AnyVal {
    def offset: Int = editor.getCaretModel.getOffset

    def commitDocument(project: Project): Unit = editor.getDocument.commit(project)
    def commitDocument(): Unit = editor.getDocument.commit(editor.getProject)

    def inScalaString(offset: Int): Boolean = {
      val afterInterpolatedInjection =
        isTokenType(offset - 1, t => t == tRBRACE || t == tIDENTIFIER) &&
          isTokenType(offset, t => t == tINTERPOLATED_STRING || t == tINTERPOLATED_MULTILINE_STRING || t == tINTERPOLATED_STRING_END)

      val previousIsStringToken =
        isTokenType(offset - 1, t => STRING_LITERAL_TOKEN_SET.contains(t) || t == tINTERPOLATED_STRING_ESCAPE)

      previousIsStringToken || afterInterpolatedInjection
    }

    def inDocComment(offset: Int): Boolean = isTokenType(offset - 1, _.isInstanceOf[ScalaDocElementType])

    private def isTokenType(offset: Int, predicate: IElementType => Boolean): Boolean = {
      if (0 <= offset && offset < editor.getDocument.getTextLength) {
        val highlighter = editor.asInstanceOf[EditorEx].getHighlighter
        val iterator = highlighter.createIterator(offset)
        predicate(iterator.getTokenType)
      } else {
        false
      }
    }
  }

  implicit class HighlighterIteratorExt(private val hiterator: HighlighterIterator) extends AnyVal {

    @inline def tokenLength: Int = hiterator.getEnd - hiterator.getStart
  }
}