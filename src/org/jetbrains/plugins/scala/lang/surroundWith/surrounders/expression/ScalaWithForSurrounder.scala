package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

/**
* @author Alexander.Podkhalyuzin
* Date: 28.04.2008
*/

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaWithForSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateDescription = "for"

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "for (a <- as) {" + super.getTemplateAsString(elements) + "}"
  }

  override def getSurroundSelectionRange(withForNode: ASTNode): TextRange = {
    val element: PsiElement = withForNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }
    val forStmt = element.asInstanceOf[ScForStatement]

    val enums = (forStmt.enumerators: @unchecked) match {
      case Some(x) => x.getNode
    }

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}