package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import psi.impl.expr.ScBlockImpl
import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._
import lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.parser._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import lang.psi.api.statements._
import com.intellij.psi.PsiWhiteSpace

/**
 * @author AlexanderPodkhalyuzin
* Date: 11.05.2008
 */

class ScalaWithParenthesisSurrounder extends ScalaExpressionSurrounder {
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

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "(" + super.getTemplateAsString(elements) + ")"
  }

  override def getTemplateDescription = "(  )"

  override def getSurroundSelectionRange(expr: ASTNode): TextRange = {
    val offset = expr.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }

  override def needParenthesis(elements: Array[PsiElement]) = false
}