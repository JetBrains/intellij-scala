package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion._
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import psi.api.expr.{ScReferenceExpression, ScInfixExpr, ScPostfixExpr}
import psi.api.statements.{ScFunction, ScFun}
import psi.api.statements.params.ScParameter
import com.intellij.codeInsight.lookup.{LookupElementBuilder, LookupElement, LookupItem}
import resolve.ResolveUtils.ScalaLookupObject
import com.intellij.psi.{PsiNamedElement, PsiDocumentManager, PsiMethod}
import extensions._

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
    val lookupStringLength = item.getLookupString.length
    item.getObject match {
      case ScalaLookupObject(named: PsiNamedElement, isNamed) if isNamed => {
        val endOffset = startOffset + lookupStringLength
        context.setAddCompletionChar(false)
        document.insertString(endOffset, " = ")
        editor.getCaretModel.moveToOffset(endOffset + 3)
      }
      case ScalaLookupObject(_: PsiMethod, _) | ScalaLookupObject(_: ScFun, _) => {
        val (count, methodName, isAccessor) = item.getObject match {
          case ScalaLookupObject(fun: ScFunction, _) => {
            val clauses = fun.paramClauses.clauses
            if (clauses.length == 0) return
            if (clauses.apply(0).isImplicit) return
            (clauses(0).parameters.length, fun.getName, false)
          }
          case ScalaLookupObject(method: PsiMethod, _) => (method.getParameterList.getParametersCount, method.getName, method.isAccessor)
          case ScalaLookupObject(fun: ScFun, _) =>
            fun.paramClauses match {
              case Seq() => return
              case clause :: clauses => (clause.length, fun.asInstanceOf[ScSyntheticFunction].name, false)
            }
        }

        val endOffset = startOffset + lookupStringLength
        val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(document)
        val element = file.findElementAt(startOffset)
        if (count == 0 && !isAccessor) {
          document.insertString(endOffset, "()")
          editor.getCaretModel.moveToOffset(endOffset + 2)
        } else if (count > 0) {
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

          // for reference invocations
          if (context.getCompletionChar == ' ') {
            context.setAddCompletionChar(false)
            document.insertString(endOffset, " _")
            editor.getCaretModel.moveToOffset(endOffset + 2)
          } else if (endOffset == document.getTextLength || document.getCharsSequence.charAt(endOffset) != '(') {
            document.insertString(endOffset, "()")
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