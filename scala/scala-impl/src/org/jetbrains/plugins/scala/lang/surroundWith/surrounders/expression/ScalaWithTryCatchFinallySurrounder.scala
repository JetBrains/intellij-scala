package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 04.05.2008
  */
class ScalaWithTryCatchFinallySurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val arrow = if (elements.length == 0) "=>" else ScalaPsiUtil.functionArrow(elements(0).getProject)
    "try {\n" + super.getTemplateAsString(elements) + s"\n} catch {\n case _ $arrow \n} finally {}"
  }

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "try / catch / finally"

  override def getSurroundSelectionRange(withTryCatchNode: ASTNode): TextRange = {
    val element: PsiElement = withTryCatchNode.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val tryCatchStmt = element.asInstanceOf[ScTry]

    val catchBlockPsiElement = tryCatchStmt.catchBlock.get
    val caseClause =
      catchBlockPsiElement.expression.get.asInstanceOf[ScBlockExpr].caseClauses.get.caseClauses.head.pattern.get

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }
}
