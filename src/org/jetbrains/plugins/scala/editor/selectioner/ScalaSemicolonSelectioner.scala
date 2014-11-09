package org.jetbrains.plugins.scala.editor.selectioner

import java.util.ArrayList

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

/**
 * Selects a statement together with its trailing semicolon.
 *
 * @author yole
 */
class ScalaSemicolonSelectioner extends ExtendWordSelectionHandlerBase {
  def canSelect(e: PsiElement) = e.isInstanceOf[ScBlockStatement]

  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor) = {
    val treeNext: ASTNode = e.getNode.getTreeNext
    val result = new ArrayList[TextRange]
    if (treeNext != null && treeNext.getElementType == ScalaTokenTypes.tSEMICOLON) {
      val r = new TextRange(e.getTextRange.getStartOffset, treeNext.getTextRange.getEndOffset)
      result.add(r)
    }
    result
  }
}