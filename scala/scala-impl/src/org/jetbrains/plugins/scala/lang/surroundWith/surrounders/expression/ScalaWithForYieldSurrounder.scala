package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

/**
  * @author Dmitry Krasilschikov
  */

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._


/*
 * Surrounds expression with for: for { <Cursor> } yield Expression
 */
class ScalaWithForYieldSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "for (a <- as) yield {" + super.getTemplateAsString(elements) + "}"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "for / yield"

  override def getSurroundSelectionRange(withForNode: ASTNode): TextRange = {
    val element: PsiElement = withForNode.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val forStmt = element.asInstanceOf[ScFor]

    val enums = (forStmt.enumerators: @unchecked) match {
      case Some(x) => x.getNode
    }

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset)
  }
}




