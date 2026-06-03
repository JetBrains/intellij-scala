package org.jetbrains.plugins.scala.editor.selectioner

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr

import java.util

class ScalaCodeBlockSelectioner extends ExtendWordSelectionHandlerBase {
  override def canSelect(e: PsiElement): Boolean = e.isInstanceOf[ScBlockExpr]

  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): util.List[TextRange] = {
    var firstChild = e.getNode.getFirstChildNode
    var lastChild = e.getNode.getLastChildNode
    if (firstChild.getElementType == ScalaTokenTypes.tLBRACE && lastChild.getElementType == ScalaTokenTypes.tRBRACE) {
      while(firstChild.getTreeNext != null && firstChild.getTreeNext.getElementType == TokenType.WHITE_SPACE) {
        firstChild = firstChild.getTreeNext
      }
      while(lastChild.getTreePrev != null && lastChild.getTreePrev.getElementType == TokenType.WHITE_SPACE) {
        lastChild = lastChild.getTreePrev
      }
      val start = firstChild.getTextRange.getEndOffset
      val end = lastChild.getTextRange.getStartOffset
      if (start >= end) new util.ArrayList[TextRange]() // '{   }' case
      else ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, new TextRange(start, end))
    } else new util.ArrayList[TextRange]
  }
}