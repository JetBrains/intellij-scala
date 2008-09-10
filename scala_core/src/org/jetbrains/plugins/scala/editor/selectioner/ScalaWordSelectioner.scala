package org.jetbrains.plugins.scala.editor.selectioner

import lang.psi.api.toplevel.templates.ScExtendsBlock
import lang.psi.api.statements.params.{ScArguments, ScParameterClause, ScParameters}
import lang.lexer.ScalaTokenTypes
import lang.parser.ScalaElementTypes
import com.intellij.openapi.util.TextRange
import com.intellij.codeInsight.editorActions.{ExtendWordSelectionHandler, ExtendWordSelectionHandlerBase}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 10.09.2008
 */

class ScalaWordSelectioner extends ExtendWordSelectionHandlerBase {
  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): java.util.List[TextRange] = {
    var result = super.select(e, editorText, cursorOffset, editor)
    e match {
      //case for selecting parameters without parenthesises
      case _: ScParameterClause | _: ScArguments => {
        val range = e.getTextRange
        if (range.contains(cursorOffset) && range.getEndOffset - range.getStartOffset != 0) {
          val start = range.getStartOffset + 1
          //just look for last parenthesis
          val end = if (e.getNode.getLastChildNode.getElementType == ScalaTokenTypes.tRPARENTHESIS) range.getEndOffset - 1 else range.getEndOffset
          result.add(new TextRange(start, end))
        }
      }
      //case for selecting extends block
      case ext: ScExtendsBlock => {
        val range = new TextRange(e.getTextRange.getStartOffset, ext.templateBody match {case Some(x) => x.getTextRange.getStartOffset case None => e.getTextRange.getEndOffset})
        if (range.contains(cursorOffset)) result.add(range)
      }
    }
    return result
  }
  def canSelect(e: PsiElement): Boolean = {
    e match {
      case _: ScParameterClause | _: ScArguments => true
      case _: ScExtendsBlock => true
      case _ => false
    }
  }
}