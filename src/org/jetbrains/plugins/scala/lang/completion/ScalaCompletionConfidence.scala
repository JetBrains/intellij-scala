package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionConfidence}
import com.intellij.util.ThreeState
import com.intellij.psi.{PsiFile, PsiElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCompletionConfidence extends CompletionConfidence {
  def shouldFocusLookup(parameters: CompletionParameters): ThreeState = {
    if (parameters.getOriginalPosition.getText == "_") return ThreeState.NO //SCL-3290 _ <space> =>
    ThreeState.YES
  }

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