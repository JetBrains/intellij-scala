package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScalaTypeSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val expression = elements(0).asInstanceOf[ScExpression]
    val result = expression.`type`().getOrAny
    s"(${super.getTemplateAsString(elements)}: ${result.presentableText(expression)})"
  }

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription: String = "(expr: Type)"

  override def getSurroundSelectionRange(withType: ASTNode): Option[TextRange] = {
    lazy val defaultRange = {
      val expr: ScExpression = withType.getPsi.asInstanceOf[ScExpression]
      val offset = expr.getTextRange.getEndOffset
      Some(new TextRange(offset, offset))
    }

    unwrapParenthesis(withType) match {
      case Some(typedExpr: ScTypedExpression) =>
        typedExpr.typeElement
          .collect { case te if te.textMatches("Any") => te.getTextRange }
          .orElse(defaultRange)
      case _ => defaultRange
    }
  }

  override protected val isApplicableToUnitExpressions: Boolean = true
}
