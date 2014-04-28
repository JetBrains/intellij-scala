package org.jetbrains.plugins.scala
package editor.selectioner

import lang.psi.api.base.ScReferenceElement
import lang.psi.api.expr.ScMethodCall
import lang.psi.api.toplevel.templates.ScExtendsBlock
import lang.psi.api.statements.params.{ScArguments, ScParameterClause}
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.util.TextRange
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.09.2008
 */

class ScalaWordSelectioner extends ExtendWordSelectionHandlerBase {
  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): java.util.List[TextRange] = {
    val result = super.select(e, editorText, cursorOffset, editor)
    e match {
      //case for selecting parameters without parenthesises
      case _: ScParameterClause | _: ScArguments => {
        val range = e.getTextRange
        if (range.getEndOffset - range.getStartOffset != 0) {
          val start = range.getStartOffset + 1
          //just look for last parenthesis
          val end = if (Set(ScalaTokenTypes.tRPARENTHESIS, ScalaTokenTypes.tRSQBRACKET).contains(
            e.getNode.getLastChildNode.getElementType
          )) range.getEndOffset - 1 else range.getEndOffset
          result.add(new TextRange(start, end))
        }
      }
      //case for selecting extends block
      case ext: ScExtendsBlock => {
        val start: Int = e.getTextRange.getStartOffset
        var end: Int = ext.templateBody match {
          case Some(x) => x.getTextRange.getStartOffset
          case None => e.getTextRange.getEndOffset
        }
        result.add(new TextRange(start, end))
        def isEmptyChar(c: Char): Boolean = c == ' ' || c == '\n'
        while (isEmptyChar(ext.getContainingFile.getText.charAt(end - 1))) end = end - 1
        if (start <= end) result.add(new TextRange(start, end))
      }
      //case for references
      case x: ScReferenceElement => {
        //choosing end offset, another to method call
        val offset = if (!x.getParent.isInstanceOf[ScMethodCall]) x.getTextRange.getEndOffset
                     else x.getParent.getTextRange.getEndOffset
        //clear result if method call
        if (x.getParent.isInstanceOf[ScMethodCall]) result.clear()
        x.qualifier match {
          case Some(qual) => {
            //get ranges for previos qualifier
            val ranges = select(qual, editorText, cursorOffset, editor).toArray(new Array[TextRange](0))
            for (fRange <- ranges if fRange.getEndOffset == qual.getTextRange.getEndOffset) {
              //cancatenating ranges
              val tRange = new TextRange(if (fRange.getStartOffset != fRange.getEndOffset) fRange.getStartOffset
                                         else { //if we have dummy range we must find td letter to concatenate ranges
                                           var end = fRange.getEndOffset
                                           var flag = true
                                           while (flag) {
                                             editorText.charAt(end) match {
                                               case ' ' | '.' | '\n' => end += 1
                                               case _ => flag = false
                                             }
                                           }
                                           end
                                         }, offset)
              result.add(tRange)
            }
            //adding dummy range for recursion
            result.add(new TextRange(offset, offset))
          }
          case None => result.add(new TextRange(offset, offset)) //adding dummy range for recursion
        }
      }
      case x: ScMethodCall => {
        x.getEffectiveInvokedExpr match {
          case ref: ScReferenceElement => return select(ref, editorText, cursorOffset, editor)
          case _ =>
        }
      }
      case _ =>
    }
    return result
  }
  def canSelect(e: PsiElement): Boolean = {
    e match {
      case _: ScParameterClause | _: ScArguments => true
      case _: ScExtendsBlock => true
      case _: ScReferenceElement => true
      case _: ScMethodCall => true
      case _ => false
    }
  }
}