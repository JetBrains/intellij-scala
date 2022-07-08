package org.jetbrains.plugins.scala
package codeInsight
package hint

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class ScFunctionDeclarationRangeHandler extends DeclarationRangeHandler[ScFunction] {

  override def getDeclarationRange(function: ScFunction): TextRange = {
    val startOffset = function.getModifierList.getTextRange match {
      case null => function.getTextOffset
      case range => range.getStartOffset
    }

    val endOffset = function.returnTypeElement
      .fold(function.paramClauses.getTextRange.getEndOffset) {
        _.getTextRange.getEndOffset
      }
    new TextRange(startOffset, endOffset)
  }
}
