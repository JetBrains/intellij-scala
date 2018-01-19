package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCompletionConfidence extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = {
    if (offset != 0) {
      val elementType: IElementType = psiFile.findElementAt(offset - 1).getNode.getElementType
      elementType match {
        case ScalaTokenTypes.tINTEGER | ScalaTokenTypes.tFLOAT => return ThreeState.YES
        case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING if psiFile.charSequence.charAt(offset - 1) == '$' =>
          return ThreeState.NO
        case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING
          if psiFile.charSequence.charAt(offset - 1) == '.' =>
          psiFile.findElementAt(offset).getPrevSibling match {
            case _: ScReferenceExpression => return ThreeState.NO
            case _ =>
          }
        case _ =>
      }
    }
    super.shouldSkipAutopopup(contextElement, psiFile, offset)
  }
}
