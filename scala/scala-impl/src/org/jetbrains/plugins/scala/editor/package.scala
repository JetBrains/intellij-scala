package org.jetbrains.plugins.scala

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType

/**
  * Nikolay.Tropin
  * 27-Oct-17
  */
package object editor {
  implicit class DocumentExt(private val document: Document) extends AnyVal {
    def commit(project: Project): Unit = PsiDocumentManager.getInstance(project).commitDocument(document)

    def lineStartOffset(offset: Int): Int =
      document.getLineStartOffset(document.getLineNumber(offset))

    def lineEndOffset(offset: Int): Int =
      document.getLineEndOffset(document.getLineNumber(offset))

    def virtualFile: Option[VirtualFile] =
      Option(FileDocumentManager.getInstance().getFile(document))
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

    def scalaFile: Option[VirtualFile] =
      Option(editor.getDocument)
        .flatMap(_.virtualFile)
        .filter(vFile => vFile.getExtension == ScalaFileType.INSTANCE.getDefaultExtension)

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