package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionConfidence._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix

final class ScalaCompletionConfidence extends CompletionConfidence {

  import lexer.ScalaTokenTypes._

  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = {
    def typedChar(c: Char): Boolean = psiFile.charSequence.charAt(offset - 1) == c

    if (offset != 0) {
      val node = psiFile.findElementAt(offset - 1).getNode
      node.getElementType match {
        case elementType if NUMBER_TOKEN_SET.contains(elementType) =>
          return ThreeState.YES
        case `tSTRING` | `tMULTILINE_STRING` if typedChar('$') =>
          return ThreeState.NO
        case `tINTERPOLATED_STRING` | `tINTERPOLATED_MULTILINE_STRING` if typedChar('.') =>
          if (isDotTypedAfterStringInjectedReference(psiFile, offset))
            return ThreeState.NO
        case _  =>
      }
    }
    super.shouldSkipAutopopup(contextElement, psiFile, offset)
  }
}

object ScalaCompletionConfidence {

  def isDotTypedAfterStringInjectedReference(psiFile: PsiFile, offset: Int): Boolean = {
    if (offset <= 2) return false
    val element = psiFile.findElementAt(offset - 2)
    if (element == null) return false
    val parent = element.getParent
    parent match {
      case _: ScInterpolatedExpressionPrefix => false
      case _: ScReferenceExpression          => true
      case _                                 => false
    }
  }
}
