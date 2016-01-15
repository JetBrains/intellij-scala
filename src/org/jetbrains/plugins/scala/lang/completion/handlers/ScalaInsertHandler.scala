package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.{AutoPopupController, CodeInsightSettings}
import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInspection.redundantBlock.RedundantBlockInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

import scala.annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
object ScalaInsertHandler {
  def getItemParametersAndAccessorStatus(item: ScalaLookupItem): (Int, String, Boolean) = {
    item.element match {
      case fun: ScFunction =>
        val clauses = fun.paramClauses.clauses
        if (clauses.isEmpty) (-1, null, false)
        else if (clauses.head.isImplicit) (-1, null, false)
        else (clauses.head.parameters.length, fun.name, false)
      case method: PsiMethod =>
        def isStringSpecialMethod: Boolean = {
          Set("hashCode", "length", "trim").contains(method.getName) &&
            method.containingClass != null &&
            method.containingClass.qualifiedName == "java.lang.String"
        }
        (method.getParameterList.getParametersCount, method.name, method.isAccessor || isStringSpecialMethod)
      case fun: ScFun =>
        if (fun.paramClauses.isEmpty) (-1, null, false)
        else {
          val clause = fun.paramClauses.head
          (clause.length, fun.asInstanceOf[ScSyntheticFunction].name, false)
        }
      case _ => (0, item.element.name, true)
    }
  }
}

class ScalaInsertHandler extends InsertHandler[LookupElement] {
  import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaInsertHandler._
  override def handleInsert(context: InsertionContext, _item: LookupElement) {
    if (!_item.isInstanceOf[ScalaLookupItem]) return
    val item = _item.asInstanceOf[ScalaLookupItem]

    val editor = context.getEditor
    val document = editor.getDocument

    val contextStartOffset = context.getStartOffset
    var (startOffset, lookupStringLength) =
      if (item.isInSimpleString) {
        val literal = context.getFile.findElementAt(contextStartOffset).getParent
        val startOffset = contextStartOffset
        val tailOffset = context.getTailOffset
        val literalOffset = literal.getTextRange.getStartOffset
        document.insertString(tailOffset, "}")
        document.insertString(startOffset, "{")
        document.insertString(literalOffset, "s")
        context.commitDocument()
        (startOffset + 2, tailOffset - startOffset)
      } else if (item.isInInterpolatedString) {
        val literal = context.getFile.findElementAt(contextStartOffset).getParent
        if (!literal.isInstanceOf[ScInterpolated]) return
        val index = literal.asInstanceOf[ScInterpolated].getInjections.lastIndexWhere { expr =>
          expr.getTextRange.getEndOffset <= contextStartOffset
        }
        val res = ScalaBasicCompletionContributor.getStartEndPointForInterpolatedString(literal.asInstanceOf[ScInterpolated],
          index, contextStartOffset - literal.getTextRange.getStartOffset)
        if (res.isEmpty) return
        val (startOffset, _) = res.get
        val tailOffset = context.getTailOffset
        document.insertString(tailOffset, "}")
        document.insertString(startOffset + literal.getTextRange.getStartOffset, "{")
        context.commitDocument()
        (startOffset + 1, tailOffset - startOffset)
      } else (contextStartOffset, context.getTailOffset - contextStartOffset)
    var endOffset = startOffset + lookupStringLength
    

    val completionChar: Char = context.getCompletionChar
    def disableParenthesesCompletionChar() {
      if (completionChar == '(' || completionChar == '{') {
        context.setAddCompletionChar(false)
      }
    }

    val some = item.someSmartCompletion
    val someNum = if (some) 1 else 0
    //val file = context.getFile //returns wrong file in evaluate expression in debugger (runtime type completion)
    val file = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
    val element =
      if (completionChar == '\t') {
        file.findElementAt(startOffset) match {
          case elem if elem.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER && elem.getParent.isInstanceOf[ScReferenceExpression]
            && elem.getParent.getParent.isInstanceOf[ScReferenceExpression] && item.getAllLookupStrings.size() > 1 =>
            val ref = elem.getParent.asInstanceOf[ScReferenceExpression]
            val newRefText = ref.getText
            val newRef = ScalaPsiElementFactory.createExpressionFromText(newRefText, ref.getManager)
            ref.getParent.replace(newRef).getFirstChild
          case elem => elem
        }
      } else file.findElementAt(startOffset)
    if (some) {
      var elem = element
      var parent = elem.getParent
      while (parent match {
        case _: ScStableCodeReferenceElement =>
          parent.getParent match {
            case _: ScThisReference | _: ScSuperReference => true
            case _ => false
          }
        case _: ScReferenceExpression => true
        case _: ScThisReference => true
        case _: ScSuperReference => true
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

    /**
     * insert parentheses in case if it's necessary
     * @param placeInto "(<caret>)" if true
     * @param openChar open char like '('
     * @param closeChar close char like ')'
     * @param withSpace add " ()" if true
     * @param withSomeNum move caret with additional shift on some completion ending
     */
    @tailrec
    def insertIfNeeded(placeInto: Boolean, openChar: Char, closeChar: Char, withSpace: Boolean, withSomeNum: Boolean) {
      def shiftEndOffset(shift: Int, withSomeNum: Boolean = withSomeNum) {
        endOffset += shift
        editor.getCaretModel.moveToOffset(endOffset + (if (withSomeNum) someNum else 0))
      }
      val documentText: String = document.getText
      val nextChar: Char =
        if (endOffset < document.getTextLength) documentText.charAt(endOffset)
        else 0.toChar
      if (!withSpace && nextChar != openChar) {
        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
          document.insertString(endOffset, s"$openChar$closeChar")
          if (placeInto) {
            shiftEndOffset(1)
          } else {
            shiftEndOffset(2)
          }
        } else {
          document.insertString(endOffset, s"$openChar")
          shiftEndOffset(1)
        }
      } else if (!withSpace && nextChar == openChar) {
        if (placeInto) {
          shiftEndOffset(1)
        } else {
          val nextNextChar = documentText.charAt(endOffset + 1)
          if (nextNextChar == closeChar) {
            shiftEndOffset(2)
          } else {
            shiftEndOffset(1)
          }
        }
      } else if (withSpace && (nextChar != ' ' || documentText.charAt(endOffset + 1) != openChar)) {
        document.insertString(endOffset, " ")
        shiftEndOffset(1, withSomeNum = false)
        insertIfNeeded(placeInto, openChar, closeChar, withSpace = false, withSomeNum = withSomeNum)
      } else if (withSpace && nextChar == ' ') {
        shiftEndOffset(1, withSomeNum = false)
        insertIfNeeded(placeInto, openChar, closeChar, withSpace = false, withSomeNum = withSomeNum)
      }
    }

    item.element match {
      case _: PsiClass | _: ScTypeAlias if context.getCompletionChar == '[' =>
        context.setAddCompletionChar(false)
        insertIfNeeded(placeInto = true, openChar = '[', closeChar = ']', withSpace = false, withSomeNum = false)
      case named: PsiNamedElement if item.isNamedParameter => //some is impossible here
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
      case _: PsiMethod if item.isInImport => moveCaretIfNeeded()
      case _: ScFun if item.isInImport => moveCaretIfNeeded()
      case fun: ScFunction if fun.name == "classOf" && fun.containingClass != null &&
        fun.containingClass.qualifiedName == "scala.Predef" =>
        context.setAddCompletionChar(false)
        insertIfNeeded(placeInto = true, openChar = '[', closeChar = ']', withSpace = false, withSomeNum = true)
      case _: PsiMethod | _: ScFun =>
        if (context.getCompletionChar != '[') {
          val (count, _, isAccessor) = getItemParametersAndAccessorStatus(item)
          if (count == 0 && !isAccessor) {
            disableParenthesesCompletionChar()
            if (item.etaExpanded) {
              document.insertString(endOffset, " _")
              endOffset += 2
              editor.getCaretModel.moveToOffset(endOffset)
            } else {
              insertIfNeeded(placeInto = context.getCompletionChar == '(', openChar = '(', closeChar = ')',
                withSpace = false, withSomeNum = false)
            }
          } else if (count > 0) {
            import org.jetbrains.plugins.scala.extensions._
            element.getParent match {
              //case for infix expressions
              case Both(ref: ScReferenceExpression, Parent(inf: ScInfixExpr)) if inf.operation == ref =>
                if (count > 1) {
                  disableParenthesesCompletionChar()
                  if (!item.etaExpanded) {
                    val openChar = if (context.getCompletionChar == '{') '{' else '('
                    val closeChar = if (context.getCompletionChar == '{') '}' else ')'
                    insertIfNeeded(placeInto = true, openChar = openChar, closeChar = closeChar, withSpace = true, withSomeNum = false)
                  } else {
                    document.insertString(endOffset, " _")
                    endOffset += 2
                    editor.getCaretModel.moveToOffset(endOffset)
                  }
                } else {
                  if (context.getCompletionChar == '{') {
                    insertIfNeeded(placeInto = true, openChar = '{', closeChar = '}', withSpace = true, withSomeNum = false)
                  } else {
                    document.insertString(endOffset, " ")
                    endOffset += 1
                    editor.getCaretModel.moveToOffset(endOffset)
                  }
                }
              // for reference invocations
              case _ =>
                if (completionChar == ' ') {
                  context.setAddCompletionChar(false)
                  document.insertString(endOffset, " ")
                  endOffset += 1
                  editor.getCaretModel.moveToOffset(endOffset + someNum)
                } else if (endOffset == document.getTextLength || document.getCharsSequence.charAt(endOffset) != '(') {
                  disableParenthesesCompletionChar()
                  if (!item.etaExpanded) {
                    if (context.getCompletionChar == '{') {
                      if (ScalaPsiUtil.getSettings(context.getProject).SPACE_BEFORE_BRACE_METHOD_CALL) {
                        insertIfNeeded(placeInto = true, openChar = '{', closeChar = '}', withSpace = true, withSomeNum = false)
                      } else {
                        insertIfNeeded(placeInto = true, openChar = '{', closeChar = '}', withSpace = false, withSomeNum = false)
                      }
                    } else {
                      insertIfNeeded(placeInto = true, openChar = '(', closeChar = ')', withSpace = false, withSomeNum = false)
                    }
                  } else {
                    document.insertString(endOffset, " _")
                    endOffset += 2
                    editor.getCaretModel.moveToOffset(endOffset)
                  }
                  AutoPopupController.getInstance(element.getProject).autoPopupParameterInfo(editor, element)
                } else if (completionChar != ',') {
                  editor.getCaretModel.moveToOffset(endOffset + 1 + someNum)
                } else moveCaretIfNeeded()
            }
          } else moveCaretIfNeeded()
        } else {
          context.setAddCompletionChar(false)
          insertIfNeeded(placeInto = true, openChar = '[', closeChar = ']', withSpace = false, withSomeNum = false)
          //do not add () or {} in this case, use will choose what he want later
        }
      case _ => moveCaretIfNeeded()
    }

    if (completionChar == ',') {
      endOffset += someNum
      context.setAddCompletionChar(false)
      document.insertString(endOffset, ",")
      endOffset += 1
      editor.getCaretModel.moveToOffset(endOffset)
    }

    if (item.isInSimpleString) {
      context.commitDocument()
      val index = contextStartOffset + 2
      val elem = context.getFile.findElementAt(index)
      elem.getNode.getElementType match {
        case ScalaTokenTypes.tIDENTIFIER =>
          val reference = elem.getParent
          reference.getParent match {
            case block: ScBlock if RedundantBlockInspection.isRedundantBlock(block) =>
              val blockEndOffset = block.getTextRange.getEndOffset
              val blockStartOffset = block.getTextRange.getStartOffset
              document.replaceString(blockEndOffset - 1, blockEndOffset, "")
              document.replaceString(blockStartOffset, blockStartOffset + 1, "")
              item.isInSimpleStringNoBraces = true
            case _ =>
          }
      }
    }
  }
}