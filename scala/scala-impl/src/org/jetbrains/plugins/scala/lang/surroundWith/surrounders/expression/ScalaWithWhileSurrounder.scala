package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

/**
  * author: Dmitry Krasilschikov
  */

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import org.jetbrains.plugins.scala.lang.psi.impl.expr._

/*
 * Surrounds expression with while: while { <Cursor> } { Expression }
 */
class ScalaWithWhileSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "while (true) {" + super.getTemplateAsString(elements) + "}"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "while"

  override def getSurroundSelectionRange(withWhileNode: ASTNode): TextRange = {
    val element: PsiElement = withWhileNode.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val whileStmt = element.asInstanceOf[ScWhileImpl]

    val conditionNode: ASTNode = (whileStmt.condition: @unchecked) match {
      case Some(c) => c.getNode
    }

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    new TextRange(startOffset, endOffset)
  }
}