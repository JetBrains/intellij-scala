package org.jetbrains.plugins.scala.lang.psi.api.expr

import impl.ScalaPsiElementFactory
import com.intellij.psi.PsiInvalidElementAccessException
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import statements.ScFunction
import types.{ScType, Nothing, ScFunctionType}

/**
 * @author Alexander Podkhalyuzin
 * Date: 14.03.2008
 */

trait ScExpression extends ScBlockStatement { self =>
  def getType(): ScType = Nothing //todo

  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    //todo: implement checking priority (when inline refactoring)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val parentNode = oldParent.getNode
    val newNode = expr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    return newNode.getPsi.asInstanceOf[ScExpression]
  }

  def expectedType: Option[ScType] = {
    getParent match {
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e eq self => b.expectedType
        case _ => None
      }
      case f: ScFunctionExpr => f.expectedType match { //There is only one way to be fun's child - it's result
        case Some(ScFunctionType(rt, _)) => Some(rt)
      }
      case _ => None
    }
  }
}