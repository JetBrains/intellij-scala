package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion._
import com.intellij.psi.{PsiDocumentManager, PsiMethod}
import psi.api.expr.{ScInfixExpr, ScPostfixExpr}
import com.intellij.codeInsight.lookup.{LookupElement, LookupItem}

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */

class ScalaInsertHandler extends InsertHandler[LookupElement] {
  override def handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.getEditor
    val document = editor.getDocument
    if (context.getCompletionChar == '(') {
      context.setAddCompletionChar(false)
    }
    val startOffset = context.getStartOffset
    item.getObject match {
      case method: PsiMethod => {
        val count = method.getParameterList.getParametersCount
        if (count > 0) {
          val endOffset = startOffset + method.getName.length
          val file = PsiDocumentManager.getInstance(method.getProject).getPsiFile(document)
          val element = file.findElementAt(startOffset)

          // for infix expressions
          if (element.getParent != null) {
            element.getParent.getParent match {
              case _: ScInfixExpr | _: ScPostfixExpr => {
                if (count > 1) {
                  document.insertString(endOffset, " ()")
                  editor.getCaretModel.moveToOffset(endOffset + 2)
                } else {
                  document.insertString(endOffset, " ")
                  editor.getCaretModel.moveToOffset(endOffset + 1)
                }
                return
              }
              case _ =>
            }
          }

          var refInv = false
          // for reference invocations
          if (endOffset == document.getTextLength() || document.getCharsSequence().charAt(endOffset) != '(') {
            document.insertString(endOffset, "()")
            refInv = true
          }
          editor.getCaretModel.moveToOffset(endOffset + 1)
          if (refInv) AutoPopupController.getInstance(element.getProject).autoPopupParameterInfo(editor, element)
        }

      }
      case _ =>
    }
  }
}