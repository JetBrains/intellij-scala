package org.jetbrains.plugins.scala.lang.completion
package handlers

import com.intellij.codeInsight.completion.{InsertionContext, InsertHandler}
import com.intellij.codeInsight.lookup.LookupElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaKeywordInsertHandler(val keyword: String) extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
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
      }
    }
  }
}