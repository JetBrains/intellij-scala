package org.jetbrains.plugins.scala
package editor.selectioner

import java.util

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

/**
 * @author ilyas
 */

class ScalaLiteralSelectioner extends ExtendWordSelectionHandlerBase {
  def canSelect(e: PsiElement): Boolean = isStringLiteral(e) || isStringLiteral(e.getParent)

  def isStringLiteral(e: PsiElement): Boolean = e match {
    case l: ScLiteral =>
      val children = l.getNode.getChildren(null)
      children.length == 1 && (children(0).getElementType == ScalaTokenTypes.tSTRING ||
        children(0).getElementType == ScalaTokenTypes.tMULTILINE_STRING)
    case _ => false
  }


  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): util.List[TextRange] = {
    val list = super.select(e, editorText, cursorOffset, editor)
    val r = e.getTextRange
    val text = e.getText
    if (text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.length > 6) {
      list.add(new TextRange(r.getStartOffset + 3, r.getEndOffset - 3))
    } else if (text.startsWith("\"") && text.endsWith("\"") && text.length > 2) {
      list.add(new TextRange(r.getStartOffset + 1, r.getEndOffset - 1))
    }
    list
  }
}