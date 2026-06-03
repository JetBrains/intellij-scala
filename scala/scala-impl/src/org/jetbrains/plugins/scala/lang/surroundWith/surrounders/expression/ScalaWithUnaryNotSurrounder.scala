package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class ScalaWithUnaryNotSurrounder extends ScalaExpressionSurrounder {

  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "!" + super.getTemplateAsString(elements).parenthesize()

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription: String = "!(expr)"

  override def isApplicable(element: PsiElement): Boolean = isBooleanExpression(element)

  override def getSurroundSelectionRange(withUnaryNot: ASTNode): Option[TextRange] =
    unwrapParenthesis(withUnaryNot) match {
      case Some(expr: ScExpression) =>
        val offset = expr.endOffset
        val range = new TextRange(offset, offset)
        Some(range)
      case _ => None
    }
}
