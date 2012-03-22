package org.jetbrains.plugins.scala.lang.completion
package handlers

import com.intellij.codeInsight.completion.{InsertionContext, InsertHandler}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.util.TextRange

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaKeywordInsertHandler(val keyword: String) extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.getEditor
    val document = editor.getDocument
    val offset = context.getStartOffset + keyword.length
    import ScalaKeyword._
    keyword match {
      case THIS | FALSE | TRUE | NULL | SUPER => // do nothing
      case _ => {
        context.setAddCompletionChar(false)
        document.insertString(offset, " ")
        editor.getCaretModel.moveToOffset(editor.getCaretModel.getOffset + 1)
        if (keyword == CASE) {
          val manager = PsiDocumentManager.getInstance(context.getProject)
          manager.commitDocument(document)
          val file = manager.getPsiFile(document)
          if (file == null) return
          CodeStyleManager.getInstance(context.getProject).
                  adjustLineIndent(file, new TextRange(context.getStartOffset, offset))
        }
      }
    }
  }
}