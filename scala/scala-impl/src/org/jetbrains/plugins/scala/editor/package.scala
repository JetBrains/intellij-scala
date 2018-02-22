package org.jetbrains.plugins.scala

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType

/**
  * Nikolay.Tropin
  * 27-Oct-17
  */
package object editor {
  implicit class DocumentExt(val document: Document) extends AnyVal {
    def commit(project: Project): Unit = PsiDocumentManager.getInstance(project).commitDocument(document)

    def lineStartOffset(offset: Int): Int =
      document.getLineStartOffset(document.getLineNumber(offset))

    def lineEndOffset(offset: Int): Int =
      document.getLineEndOffset(document.getLineNumber(offset))
  }

  implicit class EditorExt(val editor: Editor) extends AnyVal {
    def offset: Int = editor.getCaretModel.getOffset

    def commitDocument(project: Project): Unit = editor.getDocument.commit(project)

    def inScalaString(offset: Int): Boolean = {
      val afterInterpolatedInjection =
        isTokenType(offset - 1, t => t == tRBRACE || t == tIDENTIFIER) &&
          isTokenType(offset, t => t == tINTERPOLATED_STRING || t == tINTERPOLATED_STRING_END)

      val previousIsStringToken =
        isTokenType(offset - 1, t => STRING_LITERAL_TOKEN_SET.contains(t) || t == tINTERPOLATED_STRING_ESCAPE)

      previousIsStringToken || afterInterpolatedInjection
    }

    def inDocComment(offset: Int): Boolean = isTokenType(offset - 1, _.isInstanceOf[ScalaDocElementType])

    private def isTokenType(offset: Int, predicate: IElementType => Boolean): Boolean = {
      if (offset < 0 || offset >= editor.getDocument.getTextLength) return false

      val highlighter = editor.asInstanceOf[EditorEx].getHighlighter
      val iterator = highlighter.createIterator(offset)
      predicate(iterator.getTokenType)
    }
  }
}