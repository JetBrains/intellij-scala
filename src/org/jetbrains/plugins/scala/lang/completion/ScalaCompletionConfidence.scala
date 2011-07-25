package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionConfidence}
import com.intellij.util.ThreeState
import com.intellij.psi.{PsiFile, PsiElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScParenthesisedExpr, ScTuple, ScArgumentExprList}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCompletionConfidence extends CompletionConfidence {
  def shouldFocusLookup(parameters: CompletionParameters): ThreeState = {
    if (parameters.getOriginalPosition == null || parameters.getOriginalPosition.getText == "_") return ThreeState.NO //SCL-3290 _ <space> =>
    parameters.getPosition.getParent match {
      case ref: ScReferenceElement if ref.qualifier == None => {
        ref.getParent match {
          case args: ScArgumentExprList => ThreeState.NO //possible anonymous method parameter name
          case parent@(_: ScTuple | _: ScParenthesisedExpr | _: ScBlockExpr) =>
            parent.getParent match {
              case args: ScArgumentExprList => ThreeState.NO
              case _ => ThreeState.YES
            }
          case _ => ThreeState.YES
        }
      }
      case ref: ScReferenceElement => ThreeState.YES
      case _ => ThreeState.NO //keyword completion can be here in case of broken tree
    }
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