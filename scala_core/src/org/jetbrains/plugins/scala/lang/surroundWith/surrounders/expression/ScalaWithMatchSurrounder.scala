package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
 * @author AlexanderPodkhalyuzin
* Date: 28.04.2008
 */

import psi.impl.expr.ScBlockImpl
import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiWhiteSpace;

class ScalaWithMatchSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length > 1) return false
    for (val element <- elements)
      if (!isApplicable(element)) return false
    return true
  }
  override def isApplicable(element: PsiElement): Boolean = {
    element match {
      case _: ScBlockExpr => true
      case _: ScBlockImpl => false
      case _: ScExpression | _: PsiWhiteSpace => {
        true
      }
      case e => {
        e.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
      }
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
    return (if (elements.length == 1 && !needBraces(elements(0))) super.getTemplateAsString(elements)
            else "(" + super.getTemplateAsString(elements) + ")")+ " match {\ncase a  =>\n}"
  }

  override def getTemplateDescription = "match"

  override def getSurroundSelectionRange(withMatchNode: ASTNode): TextRange = {
    val whileStmt = withMatchNode.getPsi.asInstanceOf[ScMatchStmt]
    //val r = whileStmt.getNode.getLastChildNode.getTreePrev.getFirstChildNode
    val patternNode: ASTNode = whileStmt.getNode.getLastChildNode.getTreePrev.getTreePrev.getFirstChildNode.getFirstChildNode.getTreeNext.getTreeNext
    val offset = patternNode.getTextRange.getStartOffset
    patternNode.getTreeParent.removeChild(patternNode)

    return new TextRange(offset, offset);
  }
}