package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * @author Alexander Podkhalyuzin
 */

final class ScalaCompletionConfidence extends CompletionConfidence {

  import lexer.ScalaTokenTypes._

  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = {
    if (offset != 0) {
      psiFile.findElementAt(offset - 1)
        .getNode
        .getElementType match {
        case elementType if NUMBER_TOKEN_SET.contains(elementType) => return ThreeState.YES
        case `tSTRING` |
             `tMULTILINE_STRING`
          if psiFile.charSequence.charAt(offset - 1) == '$' =>
          return ThreeState.NO
        case `tINTERPOLATED_STRING` |
             `tINTERPOLATED_MULTILINE_STRING`
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
