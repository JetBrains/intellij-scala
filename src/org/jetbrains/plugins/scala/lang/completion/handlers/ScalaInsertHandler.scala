package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion._
import com.intellij.psi.{PsiDocumentManager, PsiMethod}
import com.intellij.codeInsight.lookup.{LookupElement, LookupItem}
import psi.api.statements.ScFun
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import psi.api.expr.{ScReferenceExpression, ScInfixExpr, ScPostfixExpr}

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
      case Tuple1(_: PsiMethod) | Tuple1(_: ScFun) => {
        val (count, methodName) = item.getObject match {
          case Tuple1(method: PsiMethod) => (method.getParameterList.getParametersCount, method.getName)
          case Tuple1(fun: ScFun) => (fun.paramTypes.length, fun.asInstanceOf[ScSyntheticFunction].name)
        }
        if (count > 0) {
          val endOffset = startOffset + methodName.length
          val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(document)
          val element = file.findElementAt(startOffset)

          // for infix expressions
          if (element.getParent != null && !(element.getParent.isInstanceOf[ScReferenceExpression] &&
                  element.getParent.asInstanceOf[ScReferenceExpression].qualifier != None)) {
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
          if (context.getCompletionChar == ' ') {
            context.setAddCompletionChar(false)
            document.insertString(endOffset, " _")
            editor.getCaretModel.moveToOffset(endOffset + 2)
          } else if (endOffset == document.getTextLength || document.getCharsSequence.charAt(endOffset) != '(') {
            document.insertString(endOffset, "()")
            refInv = true
            editor.getCaretModel.moveToOffset(endOffset + 1)
            AutoPopupController.getInstance(element.getProject).autoPopupParameterInfo(editor, element)
          } else {
            editor.getCaretModel.moveToOffset(endOffset + 1)
          }
        }
      }
      case _ =>
    }
  }
}