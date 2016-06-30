package org.jetbrains.plugins.scala.codeInsight.hint

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
  * @author Alefas
  * @since 29/06/16
  */
class ScFunctionDeclarationRangeHandler extends DeclarationRangeHandler[ScFunction] {
  override def getDeclarationRange(function: ScFunction): TextRange = {
    val textRange: TextRange = function.getModifierList.getTextRange
    val startOffset: Int =
      if (textRange != null) textRange.getStartOffset
      else function.getTextOffset
    val endOffset: Int = function.returnTypeElement match {
      case Some(te) => te.getTextRange.getEndOffset
      case None => function.paramClauses.getTextRange.getEndOffset
    }
     new TextRange(startOffset, endOffset)
  }
}
