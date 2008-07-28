package org.jetbrains.plugins.scala.lang.completion.handlers

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.psi.PsiMethod
/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */

class ScalaInsertHandler extends InsertHandler {
  override def handleInsert(context: InsertionContext, item: LookupElement[_]) {
    val editor = context.getEditor
    val document = editor.getDocument
    val startOffset = context.getStartOffset
    item.getObject match {
      case method: PsiMethod => {
        if (method.getParameterList.getParametersCount > 0) {
          val offset = startOffset + method.getName.length
            if (offset == document.getTextLength() || document.getCharsSequence().charAt(offset) != '(') {
              document.insertString(offset, "()");
            }
            editor.getCaretModel.moveToOffset(offset + 1);
        }
      }
      case _ =>
    }
  }
}