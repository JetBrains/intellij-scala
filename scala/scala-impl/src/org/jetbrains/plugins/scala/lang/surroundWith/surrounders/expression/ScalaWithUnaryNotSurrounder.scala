package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * User: Alexander Podkhalyuzin
  * Date: 29.09.2008
  */
class ScalaWithUnaryNotSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "!(" + super.getTemplateAsString(elements) + ")"

  override def getTemplateDescription: String = "!(expr)"

  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length != 1) return false
    elements(0) match {
      case x: ScExpression
        if x.getTypeIgnoreBaseType.getOrAny.conforms(x.projectContext.stdTypes.Boolean) => true
      case _ => false
    }
  }

  override def getSurroundSelectionRange(withUnaryNot: ASTNode): TextRange = {
    val element: PsiElement = withUnaryNot.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val expr: ScExpression = element.asInstanceOf[ScExpression]
    val offset = expr.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }
}