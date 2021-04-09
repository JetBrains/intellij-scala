package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.{AutoPopupController, CodeInsightSettings}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.codeInspection.redundantBlock.RedundantBlockInspection.isRedundantBlock
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tIDENTIFIER
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix

import scala.annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
object ScalaInsertHandler {

  val AssignmentText = " = "

  def isParameterless(method: PsiMethod): Boolean = method match {
    case _: ScFunction => false
    case _ if method.isAccessor => true
    case _ =>
      method.getName match {
        case "hashCode" |
             "length" |
             "trim" =>
          val containingClass = method.containingClass
          containingClass != null &&
            containingClass.qualifiedName == CommonClassNames.JAVA_LANG_STRING
        case _ => false
      }
  }

  def getItemParametersAndAccessorStatus(element: PsiNamedElement): (Int, Boolean) = element match {
    case fun: ScFunction =>
      val clauses = fun.paramClauses.clauses
      if (clauses.isEmpty) (-1, false)
      else {
        val clause = clauses.head
        if (clause.isImplicit || clause.isUsing) (-1, false)
        else (clause.parameters.length, false)
      }
    case method: PsiMethod =>
      (method.getParameterList.getParametersCount, isParameterless(method))
    case fun: ScFun =>
      if (fun.paramClauses.isEmpty) (-1, false)
      else (fun.paramClauses.head.length, false)
    case _ => (0, true)
  }

  private[completion] def replaceReference(reference: ScReference, text: String)
                                          (elementToBindTo: PsiNamedElement)
                                          (collector: ScReference => ScReference = identity): Unit = {
    import reference.projectContext

    val newReference = reference match {
      case _: ScReferenceExpression => createExpressionFromText(text).asInstanceOf[ScReferenceExpression]
      case _ => createReferenceFromText(text)
    }

    val node = reference.getNode
    node.getTreeParent.replaceChild(node, newReference.getNode)

    collector(newReference).bindToElement(elementToBindTo)
  }


  private[completion] final class StringInsertPreHandler extends InsertHandler[ScalaLookupItem] {

    override def handleInsert(context: InsertionContext, item: ScalaLookupItem): Unit = {
      val document = context.getDocument
      val startOffset = context.getStartOffset
      val tailOffset = context.getTailOffset

      val literalOffset = context
        .getFile
        .findElementAt(startOffset)
        .getParent
        .getTextRange
        .getStartOffset

      document.insertString(tailOffset, "}")
      document.insertString(startOffset, "{")
      document.insertString(literalOffset, "s")
      context.commitDocument()
    }
  }

  private[completion] final class StringInsertPostHandler extends InsertHandler[ScalaLookupItem] {

    override def handleInsert(context: InsertionContext, item: ScalaLookupItem): Unit = {
      context.commitDocument()

      val element = context
        .getFile
        .findElementAt(context.getStartOffset + 2)

      val maybeBlock = element.getNode.getElementType match {
        case `tIDENTIFIER` =>
          element.getParent.getParent match {
            case block: ScBlock => Some(block)
            case _ => None
          }
        case _ => None
      }

      for {
        block <- maybeBlock
        if isRedundantBlock(block)

        TextRangeExt(startOffset, endOffset) = block.getTextRange
      } {
        val document = context.getDocument
        document.replaceString(endOffset - 1, endOffset, "")
        document.replaceString(startOffset, startOffset + 1, "")

        item.isInSimpleStringNoBraces = true
      }
    }
  }
}

final class ScalaInsertHandler extends InsertHandler[ScalaLookupItem] {

  import ScalaInsertHandler._

  override def handleInsert(context: InsertionContext, item: ScalaLookupItem): Unit = {
    val InsertionContextExt(editor, document, file, project) = context
    val model = editor.getCaretModel

    var (startOffset, endOffset) = {
      val startOffset = context.getStartOffset
      val tailOffset = context.getTailOffset

      if (item.isInSimpleString) {
        new StringInsertPreHandler().handleInsert(context, item)
        (startOffset + 2, tailOffset + 2)
      } else if (item.isInInterpolatedString) {
        file.findElementAt(startOffset).getParent match {
          case literal: ScInterpolated =>
            ScalaBasicCompletionProvider.interpolatedStringBounds(literal, startOffset) match {
              case Some((offset, _)) =>
                document.insertString(tailOffset, "}")
                document.insertString(offset + literal.getTextRange.getStartOffset, "{")
                context.commitDocument()

                (offset + 1, tailOffset + 1)
              case _ => return
            }
          case _ => return
        }
      } else (startOffset, tailOffset)
    }

    val completionChar = context.getCompletionChar

    def disableParenthesesCompletionChar(): Unit = {
      if (completionChar == '(' || completionChar == '{') {
        context.setAddCompletionChar(false)
      }
    }

    val some = item.someSmartCompletion
    val someNum = if (some) 1 else 0

    val element = {
      // InsertionContext::getFile returns wrong file in evaluate expression in debugger (runtime type completion)
      val element = PsiDocumentManager
        .getInstance(project)
        .getPsiFile(document)
        .findElementAt(startOffset)

      val maybeElement = if (completionChar == '\t' &&
        item.getAllLookupStrings.size() > 1 &&
        element.getNode.getElementType == tIDENTIFIER)
        element.getParent match {
          case ref: ScReferenceExpression =>
            ref.getParent match {
              case parentRef: ScReferenceExpression =>
                val newRef = createExpressionFromText(ref.getText)(ref)
                Some(parentRef.replace(newRef).getFirstChild)
              case _ => None
            }
          case _ => None
        }
      else
        None

      maybeElement.getOrElse(element)
    }
    if (some) {
      var elem = element
      var parent = elem.getParent
      while (parent match {
        case _: ScStableCodeReference =>
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

    def moveCaretIfNeeded(): Unit = {
      if (some) {
        model.moveToOffset(endOffset + 1)
      }
    }

    /**
     * insert parentheses in case if it's necessary
     *
     * @param placeInto   "(<caret>)" if true
     * @param openChar    open char like '('
     * @param closeChar   close char like ')'
     * @param withSpace   add " ()" if true
     * @param withSomeNum move caret with additional shift on some completion ending
     */
    @tailrec
    def insertIfNeeded(placeInto: Boolean, openChar: Char, closeChar: Char, withSpace: Boolean, withSomeNum: Boolean): Unit = {
      def shiftEndOffset(shift: Int, withSomeNum: Boolean = withSomeNum): Unit = {
        endOffset += shift
        model.moveToOffset(endOffset + (if (withSomeNum) someNum else 0))
      }

      val documentText = document.getImmutableCharSequence
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

    item.getPsiElement match {
      case _: PsiClass | _: ScTypeAlias if completionChar == '[' =>
        context.setAddCompletionChar(false)
        insertIfNeeded(placeInto = true, openChar = '[', closeChar = ']', withSpace = false, withSomeNum = false)
      case _: PsiNamedElement if item.isNamedParameterOrAssignment => //some is impossible here
        val shouldAddAssignment = element.getParent match {
          case ref: ScReferenceExpression =>
            ref.getParent match {
              case ass: ScAssignment if ass.leftExpression == ref =>
                ass.getParent match {
                  case _: ScArgumentExprList => false
                  case _ => true
                }
              case _ => true
            }
          case _ => true //should be impossible
        }
        context.setAddCompletionChar(false)

        if (shouldAddAssignment) {
          document.insertString(endOffset, AssignmentText)
          endOffset += AssignmentText.length
          model.moveToOffset(endOffset)
        }
        return
      case _: PsiMethod if item.isInImport => moveCaretIfNeeded()
      case _: ScFun if item.isInImport => moveCaretIfNeeded()
      case fun: ScFunction if fun.name == "classOf" && fun.containingClass != null &&
        fun.containingClass.qualifiedName == "scala.Predef" =>
        context.setAddCompletionChar(false)
        insertIfNeeded(placeInto = true, openChar = '[', closeChar = ']', withSpace = false, withSomeNum = true)
      case method@(_: PsiMethod | _: ScFun) =>
        if (completionChar != '[') {
          val (count, isAccessor) = getItemParametersAndAccessorStatus(method)
          if (count == 0 && !isAccessor) {
            disableParenthesesCompletionChar()
            if (item.etaExpanded) {
              document.insertString(endOffset, " _")
              endOffset += 2
              model.moveToOffset(endOffset)
            } else {
              insertIfNeeded(placeInto = completionChar == '(', openChar = '(', closeChar = ')',
                withSpace = false, withSomeNum = false)
            }
          }
          else if (count > 0) {
            element.getParent match {
              //case for infix expressions
              case (ref: ScReferenceExpression) && Parent(inf: ScInfixExpr) if inf.operation == ref =>
                if (count > 1) {
                  disableParenthesesCompletionChar()
                  if (!item.etaExpanded) {
                    val openChar = if (completionChar == '{') '{' else '('
                    val closeChar = if (completionChar == '{') '}' else ')'
                    insertIfNeeded(placeInto = true, openChar = openChar, closeChar = closeChar, withSpace = true, withSomeNum = false)
                  } else {
                    document.insertString(endOffset, " _")
                    endOffset += 2
                    model.moveToOffset(endOffset)
                  }
                } else {
                  if (completionChar == '{') {
                    insertIfNeeded(placeInto = true, openChar = '{', closeChar = '}', withSpace = true, withSomeNum = false)
                  } else {
                    document.insertString(endOffset, " ")
                    endOffset += 1
                    model.moveToOffset(endOffset)
                  }
                }
              //no braces for interpolated string id
              case _: ScInterpolatedExpressionPrefix =>

              // for reference invocations
              case _ =>
                if (completionChar == ' ') {
                  context.setAddCompletionChar(false)
                  document.insertString(endOffset, " ")
                  endOffset += 1
                  model.moveToOffset(endOffset + someNum)
                } else if (endOffset == document.getTextLength || document.getCharsSequence.charAt(endOffset) != '(') {
                  disableParenthesesCompletionChar()
                  if (!item.etaExpanded) {
                    if (completionChar == '{') {
                      if (ScalaPsiUtil.getSettings(project).SPACE_BEFORE_BRACE_METHOD_CALL) {
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
                    model.moveToOffset(endOffset)
                  }
                  AutoPopupController.getInstance(element.getProject).autoPopupParameterInfo(editor, element)
                } else if (completionChar != ',') {
                  model.moveToOffset(endOffset + 1 + someNum)
                } else moveCaretIfNeeded()
            }
          } else moveCaretIfNeeded()
        } else {
          context.setAddCompletionChar(false)
          insertIfNeeded(placeInto = true, openChar = '[', closeChar = ']', withSpace = false, withSomeNum = false)
          //do not add () or {} in this case, use will choose what he want later
        }
      case _: ScTypeDefinition =>
        if (completionChar != '[') {
          //add space between the added element and the '{' in extends block when necessary
          val documentText = document.getImmutableCharSequence
          val isInTemplateParents =
            getParentOfType(element, classOf[ScTemplateParents], false, classOf[ScExtendsBlock]) != null
          val lBraceAtCaret = endOffset < documentText.length() && documentText.charAt(endOffset) == '{'
          if (lBraceAtCaret && isInTemplateParents) {
            document.insertString(endOffset, " ")
            endOffset += 1
            model.moveToOffset(endOffset)
          }
        }
        moveCaretIfNeeded()
      case _ => moveCaretIfNeeded()
    }

    if (completionChar == ',') {
      endOffset += someNum
      context.setAddCompletionChar(false)
      document.insertString(endOffset, ",")
      endOffset += 1
      model.moveToOffset(endOffset)
    }

    if (item.isInSimpleString) {
      new StringInsertPostHandler().handleInsert(context, item)
    }
  }
}
