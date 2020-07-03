package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author AlexanderPodkhalyuzin
  *         Date: 11.05.2008
  */
class ScalaWithParenthesisSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length > 1) return false
    for (element <- elements)
      if (!isApplicable(element)) return false
    true
  }

  override def isApplicable(element: PsiElement): Boolean = {
    element match {
      case _: ScBlockExpr => true
      case _: ScBlock => false
      case _: ScExpression | _: PsiWhiteSpace => true
      case e => ScalaPsiUtil.isLineTerminator(e)
    }
  }

  override def getTemplateAsString(elements: Array[PsiElement]): String = "(" + super.getTemplateAsString(elements) + ")"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "(  )"

  override def getSurroundSelectionRange(expr: ASTNode): TextRange = {
    val offset = expr.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }

  override def needParenthesis(element: PsiElement) = false
}