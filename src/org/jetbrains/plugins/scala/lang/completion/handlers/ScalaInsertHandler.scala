package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion._
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import psi.api.statements.{ScFunction, ScFun}
import com.intellij.codeInsight.lookup.LookupElement
import extensions._
import psi.api.expr._
import psi.api.toplevel.typedef.ScObject
import com.intellij.openapi.util.Condition
import com.intellij.psi.{PsiFile, PsiNamedElement, PsiMethod}
import lookups.ScalaLookupItem

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */

class ScalaInsertHandler extends InsertHandler[LookupElement] {
  override def handleInsert(context: InsertionContext, _item: LookupElement) {
    if (!_item.isInstanceOf[ScalaLookupItem]) return
    val item = _item.asInstanceOf[ScalaLookupItem]
    val editor = context.getEditor
    val document = editor.getDocument
    val completionChar: Char = context.getCompletionChar
    def disableParenthesesCompletionChar() {
      if (completionChar == '(') {
        context.setAddCompletionChar(false)
      }
    }
    var startOffset = context.getStartOffset
    val lookupStringLength = item.getLookupString.length

    var endOffset = startOffset + lookupStringLength

    val some = item.someSmartCompletion
    val someNum = if (some) 1 else 0
    val file = context.getFile
    val element = file.findElementAt(startOffset)
    if (some) {
      var elem = element
      var parent = elem.getParent
      while (parent match {
        case _: ScReferenceExpression => true
        case inf: ScInfixExpr if elem == inf.operation => true
        case pref: ScPrefixExpr if elem == pref.operation => true
        case postf: ScPostfixExpr if elem == postf.operation => true
        case _ => false
      }) {
        elem = parent
        parent = parent.getParent
      }

      val start = elem.getTextRange.getStartOffset
      val end = elem.getTextRange.getEndOffset
      document.insertString(end, ")")
      document.insertString(start, "Some(")
      startOffset += 5
      endOffset += 5
    }

    def moveCaretIfNeeded() {
      if (some) {
        editor.getCaretModel.moveToOffset(endOffset + 1)
      }
    }

    item.element match {
      case obj: ScObject if item.isInStableCodeReference  =>
        if (completionChar != '.') {
          document.insertString(endOffset, ".")
          endOffset += 1
          editor.getCaretModel.moveToOffset(endOffset)
          context.setLaterRunnable(new Runnable {
            def run() {
              AutoPopupController.getInstance(context.getProject).scheduleAutoPopup(
                context.getEditor, new Condition[PsiFile] {
                  def value(t: PsiFile): Boolean = t == context.getFile
                }
              )
            }
          })
        }
        return
      case named: PsiNamedElement if item.isNamedParameter => { //some is impossible here
        val shouldAddEqualsSign = element.getParent match {
          case ref: ScReferenceExpression =>
            ref.getParent match {
              case ass: ScAssignStmt if ass.getLExpression == ref =>
                ass.getParent match {
                  case args: ScArgumentExprList => false
                  case _ => true
                }
              case _ => true
            }
          case _ => true //should be impossible
        }
        context.setAddCompletionChar(false)
        if (shouldAddEqualsSign) {
          document.insertString(endOffset, " = ")
          endOffset += 3
          editor.getCaretModel.moveToOffset(endOffset)
        }
        return
      }
      case _: PsiMethod if item.isInImport => moveCaretIfNeeded()
      case _: ScFun if item.isInImport => moveCaretIfNeeded()
      case _: PsiMethod | _: ScFun => {

        val (count, methodName, isAccessor) = item.element match {
          case fun: ScFunction => {
            val clauses = fun.paramClauses.clauses
            if (clauses.length == 0) (-1, null, false)
            else if (clauses.apply(0).isImplicit) (-1, null, false)
            else (clauses(0).parameters.length, fun.name, false)
          }
          case method: PsiMethod =>
            (method.getParameterList.getParametersCount, method.name, method.isAccessor)
          case fun: ScFun =>
            fun.paramClauses match {
              case Seq() => (-1, null, false)
              case clause :: clauses => (clause.length, fun.asInstanceOf[ScSyntheticFunction].name, false)
            }
        }

        if (count == 0 && !isAccessor) {
          disableParenthesesCompletionChar()
          document.insertString(endOffset, "()")
          endOffset += 2
          editor.getCaretModel.moveToOffset(endOffset)
        } else if (count > 0) {
          import extensions._
          element.getParent match {
            //case for infix expressions
            case Both(ref: ScReferenceExpression, Parent(_: ScInfixExpr | _: ScPostfixExpr))
              if ref.qualifier != null =>
              if (count > 1) {
                disableParenthesesCompletionChar()
                document.insertString(endOffset, " ()")
                endOffset += 3
                editor.getCaretModel.moveToOffset(endOffset - 1)
              } else {
                document.insertString(endOffset, " ")
                endOffset += 1
                editor.getCaretModel.moveToOffset(endOffset)
              }
            // for reference invocations
            case _ =>
              if (completionChar == ' ') {
                context.setAddCompletionChar(false)
                document.insertString(endOffset, " _")
                endOffset += 2
                editor.getCaretModel.moveToOffset(endOffset + someNum)
              } else if (endOffset == document.getTextLength || document.getCharsSequence.charAt(endOffset) != '(') {
                disableParenthesesCompletionChar()
                document.insertString(endOffset, "()")
                endOffset += 2
                editor.getCaretModel.moveToOffset(endOffset - 1)
                AutoPopupController.getInstance(element.getProject).autoPopupParameterInfo(editor, element)
              } else if (completionChar != ',') {
                editor.getCaretModel.moveToOffset(endOffset + 1 + someNum)
              } else moveCaretIfNeeded()
          }
        } else moveCaretIfNeeded()
      }
      case _ => moveCaretIfNeeded()
    }

    if (completionChar == ',') {
      endOffset += someNum
      context.setAddCompletionChar(false)
      document.insertString(endOffset, ", ")
      endOffset += 2
      editor.getCaretModel.moveToOffset(endOffset)
    }
  }
}