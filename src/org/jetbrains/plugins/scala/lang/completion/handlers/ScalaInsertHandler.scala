package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion._
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import psi.api.expr.{ScReferenceExpression, ScInfixExpr, ScPostfixExpr}
import psi.api.statements.{ScFunction, ScFun}
import com.intellij.codeInsight.lookup.LookupElement
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
    val completionChar: Char = context.getCompletionChar
    if (completionChar == '(') {
      context.setAddCompletionChar(false)
    }
    val startOffset = context.getStartOffset
    val lookupStringLength = item.getLookupString.length

    val patchedObject = ScalaCompletionUtil.getScalaLookupObject(item)
    if (patchedObject == null) return
    var endOffset = startOffset + lookupStringLength

    patchedObject match {
      case ScalaLookupObject(named: PsiNamedElement, isNamed, _) if isNamed => {
        context.setAddCompletionChar(false)
        document.insertString(endOffset, " = ")
        endOffset += 3
        editor.getCaretModel.moveToOffset(endOffset)
      }
      case ScalaLookupObject(_: PsiMethod, _, _) | ScalaLookupObject(_: ScFun, _, _) => {
        patchedObject match {
          case ScalaLookupObject(_, _, true) => return
          case _ =>
        }
        val (count, methodName, isAccessor) = patchedObject match {
          case ScalaLookupObject(fun: ScFunction, _, _) => {
            val clauses = fun.paramClauses.clauses
            if (clauses.length == 0) return
            if (clauses.apply(0).isImplicit) return
            (clauses(0).parameters.length, fun.getName, false)
          }
          case ScalaLookupObject(method: PsiMethod, _, _) =>
            (method.getParameterList.getParametersCount, method.getName, method.isAccessor)
          case ScalaLookupObject(fun: ScFun, _, _) =>
            fun.paramClauses match {
              case Seq() => return
              case clause :: clauses => (clause.length, fun.asInstanceOf[ScSyntheticFunction].name, false)
            }
        }

        val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(document)
        val element = file.findElementAt(startOffset)
        if (count == 0 && !isAccessor) {
          document.insertString(endOffset, "()")
          endOffset += 2
          editor.getCaretModel.moveToOffset(endOffset)
        } else if (count > 0) {
          // for infix expressions
          if (element.getParent != null && !(element.getParent.isInstanceOf[ScReferenceExpression] &&
                  element.getParent.asInstanceOf[ScReferenceExpression].qualifier != None)) {
            element.getParent.getParent match {
              case _: ScInfixExpr | _: ScPostfixExpr => {
                if (count > 1) {
                  document.insertString(endOffset, " ()")
                  endOffset += 3
                  editor.getCaretModel.moveToOffset(endOffset - 1)
                } else {
                  document.insertString(endOffset, " ")
                  endOffset += 1
                  editor.getCaretModel.moveToOffset(endOffset)
                }
                return
              }
              case _ =>
            }
          }

          // for reference invocations
          if (completionChar == ' ') {
            context.setAddCompletionChar(false)
            document.insertString(endOffset, " _")
            endOffset += 2
            editor.getCaretModel.moveToOffset(endOffset)
          } else if (endOffset == document.getTextLength || document.getCharsSequence.charAt(endOffset) != '(') {
            document.insertString(endOffset, "()")
            endOffset += 2
            editor.getCaretModel.moveToOffset(endOffset - 1)
            AutoPopupController.getInstance(element.getProject).autoPopupParameterInfo(editor, element)
          } else if (completionChar != ',') {
            editor.getCaretModel.moveToOffset(endOffset + 1)
          }
        }
      }
      case _ =>
    }
    if (completionChar == ',') {
      context.setAddCompletionChar(false)
      document.insertString(endOffset, ", ")
      endOffset += 2
      editor.getCaretModel.moveToOffset(endOffset)
    }
  }
}