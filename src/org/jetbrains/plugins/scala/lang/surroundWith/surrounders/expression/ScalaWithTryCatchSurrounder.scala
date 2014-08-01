package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression;

/**
 * @author Dmitry Krasilschikov, alefas
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil

/**
 * Surrounds block with try - catch: try { Block } catch { <Cursor> } 
 */
class ScalaWithTryCatchSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val arrow = if (elements.length == 0) "=>" else ScalaPsiUtil.functionArrow(elements(0).getProject)
    "try {\n" + super.getTemplateAsString(elements) + s"\n}\ncatch {\n case _ $arrow \n}"
  }

  override def getTemplateDescription = "try / catch"

  override def getSurroundSelectionRange (withTryCatchNode : ASTNode) : TextRange = {
    val element: PsiElement = withTryCatchNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val tryCatchStmt = element.asInstanceOf[ScTryStmt]

    val catchBlockPsiElement: ScCatchBlock = tryCatchStmt.catchBlock.get
    val caseClause =
      catchBlockPsiElement.expression.get.asInstanceOf[ScBlockExpr].caseClauses.get.caseClauses(0).pattern.get

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }
}
