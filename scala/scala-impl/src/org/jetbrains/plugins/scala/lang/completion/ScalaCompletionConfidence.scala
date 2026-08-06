package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionConfidence._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix
import org.jetbrains.plugins.scala.lang.psi.types.ScOrType

final class ScalaCompletionConfidence extends CompletionConfidence {

  override def shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = {
    def typedChar(c: Char): Boolean = psiFile.charSequence.charAt(offset - 1) == c

    if (offset != 0) {
      val element = psiFile.findElementAt(offset - 1)
      if (element != null) {
        val node = element.getNode
        node.getElementType match {
          case elementType if ScalaTokenTypes.NUMBER_TOKEN_SET.contains(elementType) =>
            return ThreeState.YES
          case ScalaTokenTypes.`tSTRING` | ScalaTokenTypes.`tMULTILINE_STRING` if typedChar('$') =>
            return ThreeState.NO
          case ScalaTokenTypes.`tINTERPOLATED_STRING` | ScalaTokenTypes.`tINTERPOLATED_MULTILINE_STRING` if typedChar('.') =>
            if (isDotTypedAfterStringInjectedReference(psiFile, offset))
              return ThreeState.NO
          case ScalaTokenTypes.`tSTRING` | ScalaTokenTypes.`tMULTILINE_STRING` if psiFile.isScala3File =>
            if (isStringInUnionTypeExpectedPosition(element))
              return ThreeState.NO
          case _ =>
        }
      }
    }
    super.shouldSkipAutopopup(editor, contextElement, psiFile, offset)
  }
}

object ScalaCompletionConfidence {
  private[scala] def isStringInUnionTypeExpectedPosition(@Nullable leaf: PsiElement): Boolean =
    leaf != null && leaf.getParent.asOptionOf[ScStringLiteral].exists { str =>
      val expectedType = str.expectedType().map(_.removeAliasDefinitions())
      expectedType.exists(_.is[ScOrType])
    }

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
