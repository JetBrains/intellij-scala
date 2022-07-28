package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

class ScalaMethodCallFixer extends ScalaFixer {
  override def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val args = psiElement match {
      case call: ScMethodCall => call.args
      case _ => return NoOperation
    }

    val methodCall = psiElement.asInstanceOf[ScMethodCall]

    if (args.lastChild.exists(_.textMatches(")"))) {
      val ref = Option(methodCall.deepestInvokedExpr).flatMap(_.getReference.toOption)

      ref.map(_.resolve()) match {
        case Some(funDef: ScFunctionDefinition) =>
          funDef.clauses match {
            case Some(clauses) =>
              val cl = clauses.clauses
              if (cl.length < 2) return NoOperation

              val rightArgs = {
                var currentPsi = psiElement.getContainingFile.findElementAt(editor.getCaretModel.getOffset)

                while (currentPsi != null && methodCall.getTextRange.contains(currentPsi.getTextRange) &&
                  !currentPsi.isInstanceOf[ScArgumentExprList]) {
                  currentPsi = currentPsi.getParent
                }

                currentPsi match {
                  case a: ScArgumentExprList => a
                  case _ => args
                }
              }

              if (rightArgs.getParent != null) rightArgs.getParent.getParent match {
                case _: ScMethodCall => return NoOperation
                case _ =>
              }

              rightArgs.matchedParameters match {
                case mm if mm.nonEmpty && mm.head._2.paramInCode.isDefined =>
                  mm.head._2.paramInCode.get.getParent match {
                    case resolvedCl: ScParameterClause if cl.contains(resolvedCl) && resolvedCl != cl.last =>
                      moveToEnd(editor, args.getLastChild)
                      editor.getDocument.insertString(args.getLastChild.getTextRange.getEndOffset, "()")
                      return WithReformat(1)
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }

      return NoOperation
    }

    var endOffset: Int = -1
    var child = args.getFirstChild
    var flag = true

    while (child != null && flag) {
      child match {
        case errorElement: PsiErrorElement =>
          if (errorElement.getErrorDescription.indexOf("')'") >= 0) {
            endOffset = errorElement.getTextRange.getStartOffset
            flag = false
          }
        case _ =>
      }

      child = child.getNextSibling
    }

    if (endOffset == -1) endOffset = args.getTextRange.getEndOffset

    val params = args.exprs
    if (params.nonEmpty && startLine(editor, args) != startLine(editor, params.head))
      endOffset = args.getTextRange.getStartOffset + 1

    endOffset = CharArrayUtil.shiftBackward(editor.getDocument.getCharsSequence, endOffset - 1, " \t\n") + 1
    editor.getDocument.insertString(endOffset, ")")

    WithReformat(1)
  }
}

