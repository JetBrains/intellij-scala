package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIf, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, api}

/**
  * User: Alexander Podkhalyuzin
  * Date: 29.09.2008
  */
class ScalaWithIfElseConditionSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (" + super.getTemplateAsString(elements) + ") {}\nelse {}"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription: String = "if (expr) {...} else {...}"

  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length != 1) return false
    elements(0) match {
      case x: ScExpression if x.getTypeIgnoreBaseType.getOrAny.
        conforms(api.Boolean(x.getProject)) => true
      case _ => false
    }
  }

  override def getSurroundSelectionRange(withIfNode: ASTNode): TextRange = {
    val element: PsiElement = withIfNode.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }
    val ifStmt: ScIf = element.asInstanceOf[ScIf]
    val body = (ifStmt.thenExpression: @unchecked) match {
      case Some(x) => x
    }
    val offset = body.getTextRange.getStartOffset + 1
    new TextRange(offset, offset)
  }
}