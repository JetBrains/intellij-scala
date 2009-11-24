package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import psi.types.result.TypingContext
import psi.types._
import psi.api.expr._
import psi.api.base.types.ScTypeElement

class ScalaTypeSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val expression = elements(0).asInstanceOf[ScExpression]
    val typeResult = expression.getType(TypingContext.empty)
    val typeText = typeResult.map(ScType.presentableText(_)).getOrElse("Any")
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
    lazy val defaultRange = {
      val expr: ScExpression = withType.getPsi.asInstanceOf[ScExpression]
      val offset = expr.getTextRange.getEndOffset
      new TextRange(offset, offset)
    }

    withType.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y: ScTypedStmt) => y.typeElement match {
          case Some(te: ScTypeElement) => {
            if (te.getText() == "Any")
              te.getTextRange
            else
              defaultRange
          }
          case _ => defaultRange
        }
        case _ => defaultRange
      }
      case _ => defaultRange
    }
  }
}
