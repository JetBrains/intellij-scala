package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiInvalidElementAccessException}
import impl.ScalaPsiElementFactory
import implicits.{ScImplicitlyConvertible}
import types.{ScFunctionType, ScType, Nothing}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible {
  self =>

  def getType: ScType = {
    var tp = exprType
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && modCount == curModCount) {
      return tp
    }
    tp = typeWithUnderscore
    exprType = tp
    modCount = curModCount
    return tp
  }

  @volatile
  private var exprType: ScType = null

  @volatile
  private var modCount: Long = 0

  private def typeWithUnderscore: ScType = {
    getText.indexOf("_") match {
      case -1 => innerType //optimization
      case _ => {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) innerType
        else {
          new ScFunctionType(innerType, unders.map(_.getType))
        }
      }
    }
  }

  protected def innerType: ScType = Nothing

  /**
   * Returns all types in respect of implicit conversions (defined and default)
   */
  def allTypes: Seq[ScType] = {
    getType :: getImplicitTypes
  }

  /**
   * Some expression may be replaced only with another one
   */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val newExpr: ScExpression = if (ScalaPsiUtil.needParentheses(this, expr)) {
      ScalaPsiElementFactory.createExpressionFromText("(" + expr.getText + ")", getManager)
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    return newNode.getPsi.asInstanceOf[ScExpression]
  }


  def expectedType: Option[ScType] = ExpectedTypes.expectedExprType(this)
}