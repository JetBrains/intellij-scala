package org.jetbrains.plugins.scala
package editor.selectioner

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import lang.lexer.ScalaTokenTypes
import lang.psi.api.base.ScLiteral

/**
 * @author ilyas
 */

class ScalaLiteralSelectioner extends ExtendWordSelectionHandlerBase {
  def canSelect(e: PsiElement) = isStringLiteral(e) || isStringLiteral(e.getParent)

  def isStringLiteral(e: PsiElement) = e match {
    case l: ScLiteral => {
      val children = l.getNode.getChildren(null)
      children.length == 1 && (children(0).getElementType == ScalaTokenTypes.tSTRING ||
              children(0).getElementType == ScalaTokenTypes.tMULTILINE_STRING)
    }
    case _ => false
  }


  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor) = {
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