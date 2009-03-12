package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._
import lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.parser._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaWithTryCatchFinallySurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "try {\n" + super.getTemplateAsString(elements) + "\n}\ncatch {\n case _ => \n}\n finally {}"
  }

  override def getTemplateDescription = "try / catch / finally"

  override def getSurroundSelectionRange (withTryCatchNode : ASTNode) : TextRange = {
    val element: PsiElement = withTryCatchNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val tryCatchStmt = element.asInstanceOf[ScTryStmt]

    val catchBlockPsiElement = tryCatchStmt.catchBlock match {case Some(x) => x}
    val caseClause = catchBlockPsiElement.getNode().getFirstChildNode().getTreeNext().getTreeNext().
            getTreeNext().getTreeNext().getFirstChildNode().getFirstChildNode().getTreeNext().getTreeNext().getPsi

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }
}