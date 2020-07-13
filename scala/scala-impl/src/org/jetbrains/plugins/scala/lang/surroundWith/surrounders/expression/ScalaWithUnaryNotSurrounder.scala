package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.types.api

/**
  * User: Alexander Podkhalyuzin
  * Date: 29.09.2008
  */
class ScalaWithUnaryNotSurrounder extends ScalaExpressionSurrounder {

  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "!" + super.getTemplateAsString(elements).parenthesize()

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription: String = "!(expr)"

  override def isApplicable(elements: Array[PsiElement]): Boolean = elements match {
    case Array(expression: ScExpression) =>
      import expression.projectContext
      expression.getTypeIgnoreBaseType.exists(_.conforms(api.Boolean))
    case _ => false
  }

  override def getSurroundSelectionRange(withUnaryNot: ASTNode): TextRange = {
    val expression = withUnaryNot.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x: ScExpression => x
    }

    val offset = expression.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }
}