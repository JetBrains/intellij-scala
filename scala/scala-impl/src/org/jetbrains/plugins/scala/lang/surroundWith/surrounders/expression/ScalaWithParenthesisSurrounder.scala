package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaWithParenthesisSurrounder extends ScalaExpressionSurrounder {

  override def isApplicable(element: PsiElement): Boolean = {
    element match {
      case _: ScBlockExpr => true
      case _: ScBlock => false
      case _: ScExpression | _: PsiWhiteSpace => super.isApplicable(element)
      case _ => false
    }
  }

  override def getTemplateAsString(elements: Array[PsiElement]): String = "(" + super.getTemplateAsString(elements) + ")"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "(  )"

  override def getSurroundSelectionRange(expr: ASTNode): Option[TextRange] = {
    val offset = expr.getTextRange.getEndOffset
    val range = new TextRange(offset, offset)
    Some(range)
  }

  override def needParenthesis(element: PsiElement) = false

  override protected val isApplicableToUnitExpressions: Boolean = true
}
