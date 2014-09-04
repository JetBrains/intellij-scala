package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCompletionConfidence extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = {
    if (offset != 0) {
      val elementType: IElementType = psiFile.findElementAt(offset - 1).getNode.getElementType
      elementType match {
        case ScalaTokenTypes.tINTEGER | ScalaTokenTypes.tFLOAT => return ThreeState.YES
        case _ =>
      }
    }
    super.shouldSkipAutopopup(contextElement, psiFile, offset)
  }
}