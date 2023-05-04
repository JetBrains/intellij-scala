package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.types.api

class ScalaWithUnaryNotSurrounder extends ScalaExpressionSurrounder {

  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "!" + super.getTemplateAsString(elements).parenthesize()

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription: String = "!(expr)"

  override def isApplicable(elements: Array[PsiElement]): Boolean = elements match {
    case Array(expression: ScExpression) =>
      import expression.projectContext
      expression.getTypeIgnoreBaseType.exists(_.conforms(api.Boolean))
    case _ => false
  }

  override def getSurroundSelectionRange(editor: Editor, withUnaryNot: ASTNode): TextRange = {
    val expression = unwrapParenthesis(withUnaryNot) match {
      case Some(expr: ScExpression) => expr
      case _ => return withUnaryNot.getTextRange
    }

    val offset = expression.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }
}
