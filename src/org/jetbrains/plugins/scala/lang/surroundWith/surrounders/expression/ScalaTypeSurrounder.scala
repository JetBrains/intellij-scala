package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaExpressionSurrounder

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import psi.types.result.TypingContext
import psi.types._
import psi.api.expr._

class ScalaTypeSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val expression = elements(0).asInstanceOf[ScExpression]
    def higherPrecedenceThan_:(infixOp: String) = {
      infixOp.forall(Character.isLetter(_)) || (infixOp.length == 1 && "|^&<>=!".contains(infixOp.head))
    }
    val requiresParens = expression match {
      case inf: ScInfixExpr if !higherPrecedenceThan_:(inf.operation.getText) => true
      case _ => false
    }
    val typeResult = expression.getType(TypingContext.empty)
    val typeText = typeResult.map(ScType.presentableText(_)).getOrElse("Any")
    if (requiresParens)
      "((" + super.getTemplateAsString(elements) + "): " + typeText + ")"
    else
      "(" + super.getTemplateAsString(elements) + ": " + typeText + ")"
  }
  override def getTemplateDescription: String = "(expr: Type)"
  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length != 1) return false
    elements(0) match {
      case x: ScExpression => true
      case _ => return false
    }
  }
  override def getSurroundSelectionRange(withType: ASTNode): TextRange = {
    val element: PsiElement = withType.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val expr: ScExpression = element.asInstanceOf[ScExpression]
    val offset = expr.getTextRange.getEndOffset
    new TextRange(offset,offset)
  }
}