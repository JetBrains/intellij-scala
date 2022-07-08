package org.jetbrains.plugins.scala.editor.selectioner

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

import java.util

/**
  * Selects a statement together with its trailing semicolon.
 */
class ScalaSemicolonSelectioner extends ExtendWordSelectionHandlerBase {
  override def canSelect(e: PsiElement): Boolean = e.isInstanceOf[ScBlockStatement]

  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): util.ArrayList[TextRange] = {
    val treeNext: ASTNode = e.getNode.getTreeNext
    val result = new util.ArrayList[TextRange]
    if (treeNext != null && treeNext.getElementType == ScalaTokenTypes.tSEMICOLON) {
      val r = new TextRange(e.getTextRange.getStartOffset, treeNext.getTextRange.getEndOffset)
      result.add(r)
    }
    result
  }
}