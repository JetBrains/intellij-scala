package org.jetbrains.plugins.scala
package editor.selectioner

import java.util.ArrayList

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement}

/**
 * @author yole
 */

class ScalaStatementGroupSelectioner extends ExtendWordSelectionHandlerBase {
  def canSelect(e: PsiElement) = {
    e match {
      case _: ScBlockStatement => true
      case _: PsiComment => true
      case _ => false
    }
  }

  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): java.util.List[TextRange] = {
    val parent: PsiElement = e.getParent

    if (!(parent.isInstanceOf[ScBlock])) {
      return new ArrayList[TextRange]
    }

    def back(e: PsiElement) = e.getPrevSibling
    def forward(e: PsiElement) = e.getNextSibling
    val startElement = skipWhitespace(findGroupBoundary(e, back, ScalaTokenTypes.tLBRACE), forward)
    val endElement = skipWhitespace(findGroupBoundary(e, forward, ScalaTokenTypes.tRBRACE), back)

    val range: TextRange = new TextRange(startElement.getTextRange.getStartOffset, endElement.getTextRange.getEndOffset)
    return ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, range)
  }

  def findGroupBoundary(startElement: PsiElement, step: PsiElement => PsiElement, stopAt: IElementType): PsiElement = {
    var current: PsiElement = startElement
    while (step(current) != null) {
      val sibling: PsiElement = step(current)
      sibling match {
        case leaf: LeafPsiElement =>
          if (leaf.getElementType == stopAt) return current
          if (ScalaPsiUtil.isLineTerminator(leaf)) {
            val strings: Array[String] = LineTokenizer.tokenize(leaf.getText.toCharArray, false)
            if (strings.length > 2) {
              return current
            }
          }
        case _ =>
      }
      current = sibling
    }
    return current
  }

  def skipWhitespace(start: PsiElement, step: PsiElement => PsiElement): PsiElement = {
    var current = start
    while(current.isInstanceOf[PsiWhiteSpace]) {
      current = step(current)
    }
    return current
  }
}
