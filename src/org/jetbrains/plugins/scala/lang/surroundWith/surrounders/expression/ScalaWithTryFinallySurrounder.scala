package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

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
* Date: 28.04.2008
*/

class ScalaWithTryFinallySurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "try {\n" + super.getTemplateAsString(elements) + "\n}\nfinally a"
  }

  override def getTemplateDescription = "try / finally"

  override def getSurroundSelectionRange (withTryCatchNode : ASTNode) : TextRange = {
    val element: PsiElement = withTryCatchNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val tryCatchStmt = element.asInstanceOf[ScTryStmt]
    val caseClause = tryCatchStmt.getNode().getLastChildNode().getLastChildNode().getPsi

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }

}