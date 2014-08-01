package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

/**
 * @author AlexanderPodkhalyuzin
* Date: 28.04.2008
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr._
import psi.ScalaPsiUtil
import com.intellij.psi.PsiWhiteSpace;

class ScalaWithMatchSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length > 1) return false
    for (element <- elements)
      if (!isApplicable(element)) return false
    true
  }
  override def isApplicable(element: PsiElement): Boolean = {
    element match {
      case _: ScBlockExpr => true
      case _: ScBlock => false
      case _: ScExpression | _: PsiWhiteSpace => true
      case e => ScalaPsiUtil.isLineTerminator(e)
    }
  }

  private def needBraces(expr: PsiElement): Boolean = {
    expr match {
      case _: ScDoStmt | _: ScIfStmt | _: ScTryStmt | _: ScForStatement
          | _: ScWhileStmt | _: ScThrowStmt | _: ScReturnStmt => true
      case _ => false
    }
  }

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val arrow = if (elements.length == 0) "=>" else ScalaPsiUtil.functionArrow(elements(0).getProject)
    (if (elements.length == 1 && !needBraces(elements(0))) super.getTemplateAsString(elements)
    else "(" + super.getTemplateAsString(elements) + ")")+ s" match {\ncase a  $arrow\n}"
  }

  override def getTemplateDescription = "match"

  override def getSurroundSelectionRange(withMatchNode: ASTNode): TextRange = {
    val element: PsiElement = withMatchNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val whileStmt = element.asInstanceOf[ScMatchStmt]

    val patternNode: ASTNode = whileStmt.getNode.getLastChildNode.getTreePrev.getTreePrev.getFirstChildNode.getFirstChildNode.getTreeNext.getTreeNext
    val offset = patternNode.getTextRange.getStartOffset
    patternNode.getTreeParent.removeChild(patternNode)

    new TextRange(offset, offset)
  }
}